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
class GrpcJsAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.grpc"
    override val name = "gRPC JS Analyzer"
    override val supportedLanguages = listOf("typescript", "javascript")
    override val supportedFrameworks = listOf("express", "nestjs", "node")

    // new SomeServiceClient("host:port", credentials)
    private val grpcClientPattern = Regex(
        """new\s+(\w+(?:Service)?Client)\s*\(\s*['"`]([^'"`]+)['"`]""",
    )

    // grpc.credentials.createInsecure() or credentials.createSsl() nearby
    private val grpcCredentialsPattern = Regex(
        """grpc\.credentials\.(createInsecure|createSsl)\s*\(""",
    )

    // proto imports: require('path/to/service.proto') or protoLoader.loadSync(...)
    private val protoImportPattern = Regex(
        """(?:loadSync|require)\s*\(\s*['"`]([^'"`]+\.proto)['"`]""",
    )

    // @grpc/grpc-js import detection
    private val grpcImportPattern = Regex(
        """(?:import|require)\s*(?:\{[^}]+\}\s*from\s*)?['"`]@grpc/grpc-js['"`]""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // Only process files that import @grpc/grpc-js
            val hasGrpcImport = grpcImportPattern.containsMatchIn(file.content)

            if (hasGrpcImport) {
                // Match gRPC client instantiations
                grpcClientPattern.findAll(file.content).forEach { match ->
                    val clientClass = match.groupValues[1]
                    val address = match.groupValues[2]
                    val target = ServiceNameResolver.resolveFromAddress(address, context.envVariables)
                        ?: return@forEach

                    val location = SourceLocationExtractor.extract(file, match)
                    dependencies.add(
                        DetectedDependency(
                            source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                            target = target,
                            protocol = "gRPC",
                            method = null,
                            endpoint = clientClass,
                            confidence = 0.9,
                            detectedBy = id,
                            sourceLocations = listOf(location),
                        ),
                    )
                }

                // Match proto file imports
                protoImportPattern.findAll(file.content).forEach { match ->
                    val protoPath = match.groupValues[1]
                    val protoName = protoPath.substringAfterLast("/").removeSuffix(".proto")

                    val location = SourceLocationExtractor.extract(file, match)
                    dependencies.add(
                        DetectedDependency(
                            source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                            target = protoName,
                            protocol = "gRPC",
                            method = null,
                            endpoint = protoPath,
                            confidence = 0.65,
                            detectedBy = "$id.proto",
                            sourceLocations = listOf(location),
                        ),
                    )
                }
            }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies" }
        return dependencies
    }
}
