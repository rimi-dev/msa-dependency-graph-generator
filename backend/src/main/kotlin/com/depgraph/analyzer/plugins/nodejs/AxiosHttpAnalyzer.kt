package com.depgraph.analyzer.plugins.nodejs

import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.model.SourceLocation
import com.depgraph.analyzer.plugin.AnalyzerPlugin
import com.depgraph.analyzer.util.ServiceNameResolver
import com.depgraph.analyzer.util.SourceLocationExtractor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class AxiosHttpAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.axios"
    override val name = "Axios HTTP Analyzer"
    override val supportedLanguages = listOf("typescript", "javascript")
    override val supportedFrameworks = listOf("express", "nestjs", "node")

    // axios.get("http://..."), axios.post("http://...") 등
    private val axiosMethodPattern = Regex(
        """axios\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // axios.get(`http://...`) — 템플릿 리터럴
    private val axiosMethodTemplateLiteralPattern = Regex(
        """axios\s*\.\s*(get|post|put|delete|patch)\s*\(\s*`(https?://[^`]+)`""",
    )

    // axios({ url: "http://..." }) 또는 axios("http://...")
    private val axiosDirectPattern = Regex(
        """axios\s*\(\s*(?:\{[^}]*url\s*:\s*)?['"`](https?://[^'"`]+)['"`]""",
    )

    // 환경변수 참조 패턴
    private val envVarPattern = Regex(
        """process\.env\.([A-Z_]+(?:URL|HOST|ENDPOINT|BASE)[A-Z_]*)""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // axios 메서드 호출 매칭
            axiosMethodPattern.findAll(file.content).forEach { match ->
                val method = match.groupValues[1].uppercase()
                val url = match.groupValues[2]
                val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = method,
                        endpoint = ServiceNameResolver.extractPath(url),
                        confidence = 0.9,
                        detectedBy = id,
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // 템플릿 리터럴을 사용하는 axios 메서드 호출 매칭
            axiosMethodTemplateLiteralPattern.findAll(file.content).forEach { match ->
                val method = match.groupValues[1].uppercase()
                val url = match.groupValues[2].replace(Regex("""\$\{[^}]+\}"""), "")
                val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = method,
                        endpoint = ServiceNameResolver.extractPath(url),
                        confidence = 0.85,
                        detectedBy = "$id.template",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // axios 직접 호출 매칭
            axiosDirectPattern.findAll(file.content).forEach { match ->
                val url = match.groupValues[1]
                val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = null,
                        endpoint = ServiceNameResolver.extractPath(url),
                        confidence = 0.85,
                        detectedBy = id,
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // URL 발견을 위한 환경변수 참조 매칭
            envVarPattern.findAll(file.content).forEach { match ->
                val envKey = match.groupValues[1]
                val envValue = context.envVariables[envKey] ?: return@forEach
                val target = ServiceNameResolver.resolveFromUrl(envValue, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = null,
                        endpoint = null,
                        confidence = 0.7,
                        detectedBy = "$id.envvar",
                        sourceLocations = listOf(location),
                    ),
                )
            }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies" }
        return dependencies
    }
}
