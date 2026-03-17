package com.depgraph.analyzer.detectors

import com.depgraph.analyzer.config.ConfigFileParser
import com.depgraph.analyzer.model.DetectedService
import com.depgraph.analyzer.plugin.ServiceDetectorPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class PackageJsonDetector(
    private val configFileParser: ConfigFileParser,
) : ServiceDetectorPlugin {

    override val id = "detector.package-json"

    override fun detect(projectRoot: Path): List<DetectedService> {
        val services = mutableListOf<DetectedService>()

        // Walk up to 3 levels deep to find package.json files
        Files.walk(projectRoot, 3).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString() == "package.json" }
                .forEach { packageJsonPath ->
                    val parsed = configFileParser.parsePackageJson(packageJsonPath)
                    val serviceName = extractServiceName(parsed, packageJsonPath) ?: return@forEach

                    // Skip root package.json for monorepos (no main/scripts.start usually)
                    val isWorkspaceRoot = parsed.containsKey("workspaces")
                    if (isWorkspaceRoot) return@forEach

                    val framework = detectFramework(parsed)
                    val serviceDir = packageJsonPath.parent

                    services.add(
                        DetectedService(
                            id = "$id:$serviceName",
                            name = serviceName,
                            language = "typescript",
                            framework = framework,
                            rootPath = serviceDir.toString(),
                        ),
                    )
                    log.debug { "[$id] Detected service '$serviceName' (framework=$framework) at $serviceDir" }
                }
        }

        log.info { "[$id] Detected ${services.size} services from package.json files" }
        return services
    }

    private fun extractServiceName(parsed: Map<String, Any>, path: Path): String? {
        val name = parsed["name"] as? String ?: return path.parent.fileName.toString()
        // Normalize scoped packages: @org/service-name -> service-name
        return name.substringAfterLast("/").ifBlank { name }
    }

    private fun detectFramework(parsed: Map<String, Any>): String? {
        @Suppress("UNCHECKED_CAST")
        val dependencies = (parsed["dependencies"] as? Map<String, Any> ?: emptyMap()) +
            (parsed["devDependencies"] as? Map<String, Any> ?: emptyMap())

        return when {
            "@nestjs/core" in dependencies -> "nestjs"
            "express" in dependencies -> "express"
            "fastify" in dependencies -> "fastify"
            "koa" in dependencies -> "koa"
            "hapi" in dependencies || "@hapi/hapi" in dependencies -> "hapi"
            else -> null
        }
    }
}
