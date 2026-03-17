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
class GotHttpAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.got"
    override val name = "Got HTTP Analyzer"
    override val supportedLanguages = listOf("typescript", "javascript")
    override val supportedFrameworks = listOf("express", "nestjs", "node")

    // got("http://...") direct call
    private val gotDirectPattern = Regex(
        """got\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // got.get("http://..."), got.post("http://..."), etc.
    private val gotMethodPattern = Regex(
        """got\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // Match got.METHOD("url") calls
            gotMethodPattern.findAll(file.content).forEach { match ->
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

            // Match got("url") direct calls
            gotDirectPattern.findAll(file.content).forEach { match ->
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
