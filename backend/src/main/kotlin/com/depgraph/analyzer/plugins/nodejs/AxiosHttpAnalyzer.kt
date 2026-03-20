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

    // axios.get("http://..."), axios.post("http://..."), etc.
    private val axiosMethodPattern = Regex(
        """axios\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // axios.get(`http://...`) — template literal
    private val axiosMethodTemplateLiteralPattern = Regex(
        """axios\s*\.\s*(get|post|put|delete|patch)\s*\(\s*`(https?://[^`]+)`""",
    )

    // axios({ url: "http://..." }) or axios("http://...")
    private val axiosDirectPattern = Regex(
        """axios\s*\(\s*(?:\{[^}]*url\s*:\s*)?['"`](https?://[^'"`]+)['"`]""",
    )

    // env var reference pattern
    private val envVarPattern = Regex(
        """process\.env\.([A-Z_]+(?:URL|HOST|ENDPOINT|BASE)[A-Z_]*)""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // Match axios method calls
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

            // Match axios method calls with template literals
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

            // Match axios direct calls
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

            // Match env var references for URL discovery
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
