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
class NestJsClientAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.nestjs.client"
    override val name = "NestJS Client Analyzer"
    override val supportedLanguages = listOf("typescript")
    override val supportedFrameworks = listOf("nestjs")

    // @Client({ name: 'service-name', ... })
    private val nestClientPattern = Regex(
        """@Client\s*\(\s*\{[^}]*name\s*:\s*['"`]([^'"`]+)['"`]""",
    )

    // httpService.get("http://..."), httpService.post("http://..."), etc.
    private val nestHttpServicePattern = Regex(
        """httpService\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // ClientProxy.send('pattern', data) or ClientProxy.emit('pattern', data)
    private val clientProxySendPattern = Regex(
        """(\w+)\s*\.\s*(send|emit)\s*\(\s*(?:\{[^}]*cmd\s*:\s*['"`]([^'"`]+)['"`][^}]*\}|['"`]([^'"`]+)['"`])""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // @Client decorator - microservice client injection
            nestClientPattern.findAll(file.content).forEach { match ->
                val clientName = match.groupValues[1]

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = clientName,
                        protocol = "MQ",
                        method = null,
                        endpoint = null,
                        confidence = 0.85,
                        detectedBy = "$id.decorator",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // HttpService calls
            nestHttpServicePattern.findAll(file.content).forEach { match ->
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
                        detectedBy = "$id.httpservice",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // ClientProxy.send/emit calls
            clientProxySendPattern.findAll(file.content).forEach { match ->
                val proxyVar = match.groupValues[1]
                val operation = match.groupValues[2]
                val pattern = match.groupValues[3].takeIf { it.isNotEmpty() }
                    ?: match.groupValues[4].takeIf { it.isNotEmpty() }
                    ?: return@forEach

                // Skip obvious non-proxy variables
                if (proxyVar in setOf("this", "res", "req", "response", "request", "console")) return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = proxyVar,
                        protocol = "MQ",
                        method = operation.uppercase(),
                        endpoint = pattern,
                        confidence = 0.75,
                        detectedBy = "$id.proxy",
                        sourceLocations = listOf(location),
                    ),
                )
            }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies" }
        return dependencies
    }
}
