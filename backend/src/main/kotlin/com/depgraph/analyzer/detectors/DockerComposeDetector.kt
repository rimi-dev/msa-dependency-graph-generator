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
class DockerComposeDetector(
    private val configFileParser: ConfigFileParser,
) : ServiceDetectorPlugin {

    override val id = "detector.docker-compose"

    private val dockerComposeFileNames = listOf(
        "docker-compose.yml",
        "docker-compose.yaml",
        "docker-compose.override.yml",
        "docker-compose.override.yaml",
    )

    override fun detect(projectRoot: Path): List<DetectedService> {
        val services = mutableListOf<DetectedService>()

        dockerComposeFileNames.forEach { fileName ->
            val composeFile = projectRoot.resolve(fileName)
            if (Files.exists(composeFile)) {
                log.debug { "[$id] Parsing $fileName at $projectRoot" }
                val parsed = configFileParser.parseDockerCompose(composeFile)
                val serviceMap = configFileParser.extractDockerComposeServices(parsed)

                serviceMap.forEach { (serviceName, serviceConfig) ->
                    val image = serviceConfig["image"] as? String
                    val build = serviceConfig["build"]
                    val language = detectLanguageFromConfig(serviceConfig, image)
                    val framework = detectFrameworkFromConfig(serviceConfig)

                    // 빌드 기반 서비스의 루트 경로 결정
                    val rootPath = when (build) {
                        is String -> projectRoot.resolve(build).toString()
                        is Map<*, *> -> {
                            val context = build["context"] as? String
                            if (context != null) projectRoot.resolve(context).toString()
                            else projectRoot.resolve(serviceName).toString()
                        }
                        else -> projectRoot.resolve(serviceName).toString()
                    }

                    services.add(
                        DetectedService(
                            id = "$id:$serviceName",
                            name = serviceName,
                            language = language,
                            framework = framework,
                            rootPath = rootPath,
                        ),
                    )
                }
            }
        }

        log.info { "[$id] Detected ${services.size} services from docker-compose" }
        return services
    }

    private fun detectLanguageFromConfig(config: Map<String, Any>, image: String?): String {
        @Suppress("UNCHECKED_CAST")
        val environment = when (val env = config["environment"]) {
            is Map<*, *> -> env.keys.filterIsInstance<String>().toSet()
            is List<*> -> env.filterIsInstance<String>().map { it.substringBefore("=") }.toSet()
            else -> emptySet()
        }

        return when {
            image?.contains("node") == true -> "javascript"
            image?.contains("python") == true -> "python"
            image?.contains("java") == true || image?.contains("openjdk") == true -> "java"
            image?.contains("golang") == true || image?.contains("go:") == true -> "go"
            environment.any { it.startsWith("NODE_") || it == "NPM_CONFIG_LOGLEVEL" } -> "javascript"
            else -> "unknown"
        }
    }

    private fun detectFrameworkFromConfig(config: Map<String, Any>): String? {
        @Suppress("UNCHECKED_CAST")
        val labels = config["labels"] as? Map<String, String> ?: emptyMap()
        return labels["service.framework"]
    }
}
