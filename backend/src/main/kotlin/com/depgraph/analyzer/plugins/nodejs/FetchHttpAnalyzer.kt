package com.depgraph.analyzer.plugins.nodejs

import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.plugin.AnalyzerPlugin
import com.depgraph.analyzer.util.ServiceNameResolver
import com.depgraph.analyzer.util.SourceLocationExtractor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class FetchHttpAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.fetch"
    override val name = "Fetch/Node-Fetch HTTP Analyzer"
    override val supportedLanguages = listOf("typescript", "javascript")
    override val supportedFrameworks = listOf("express", "nestjs", "node")

    // fetch("http://...") or fetch('http://...') or fetch(`http://...`)
    private val fetchPattern = Regex(
        """fetch\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // fetch with method option: fetch("url", { method: "POST" })
    private val fetchWithMethodPattern = Regex(
        """fetch\s*\(\s*['"`](https?://[^'"`]+)['"`]\s*,\s*\{[^}]*method\s*:\s*['"`]([A-Z]+)['"`]""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // 먼저 명시적 method 옵션이 있는 fetch 호출 추출
            val withMethodMatches = fetchWithMethodPattern.findAll(file.content).toList()
            val withMethodRanges = withMethodMatches.map { it.range }

            withMethodMatches.forEach { match ->
                val url = match.groupValues[1]
                val method = match.groupValues[2].uppercase()
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

            // 이후 단순 fetch 호출 매칭 (위에서 이미 매칭된 것은 제외)
            fetchPattern.findAll(file.content)
                .filter { match -> withMethodRanges.none { it.contains(match.range.first) } }
                .forEach { match ->
                    val url = match.groupValues[1]
                    val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                    val location = SourceLocationExtractor.extract(file, match)
                    dependencies.add(
                        DetectedDependency(
                            source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                            target = target,
                            protocol = "HTTP",
                            method = "GET",
                            endpoint = ServiceNameResolver.extractPath(url),
                            confidence = 0.85,
                            detectedBy = id,
                            sourceLocations = listOf(location),
                        ),
                    )
                }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies" }
        return dependencies
    }
}
