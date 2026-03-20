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
class NestCliDetector(
    private val configFileParser: ConfigFileParser,
) : ServiceDetectorPlugin {

    override val id = "detector.nest-cli"

    override fun detect(projectRoot: Path): List<DetectedService> {
        val nestCliFile = projectRoot.resolve("nest-cli.json")
        if (!Files.exists(nestCliFile)) return emptyList()

        log.debug { "[$id] Parsing nest-cli.json at $projectRoot" }
        val parsed = configFileParser.parseNestCliJson(nestCliFile)

        // Standard NestJS project (non-monorepo)
        if (parsed["monorepo"] != true) {
            val name = projectRoot.fileName.toString()

            return listOf(
                DetectedService(
                    id = "$id:$name",
                    name = name,
                    language = "typescript",
                    framework = "nestjs",
                    rootPath = projectRoot.toString(),
                ),
            ).also { log.info { "[$id] Detected NestJS project: ${it.first().name}" } }
        }

        // NestJS monorepo - extract all apps
        @Suppress("UNCHECKED_CAST")
        val projects = parsed["projects"] as? Map<String, Any> ?: emptyMap()
        val services = projects.map { (projectName, projectConfig) ->
            @Suppress("UNCHECKED_CAST")
            val config = projectConfig as? Map<String, Any> ?: emptyMap()
            val type = config["type"] as? String ?: "application"
            val root = config["root"] as? String ?: projectName

            DetectedService(
                id = "$id:$projectName",
                name = projectName,
                language = "typescript",
                framework = if (type == "application") "nestjs" else "nestjs-lib",
                rootPath = projectRoot.resolve(root).toString(),
            )
        }

        log.info { "[$id] Detected ${services.size} NestJS monorepo projects" }
        return services
    }
}
