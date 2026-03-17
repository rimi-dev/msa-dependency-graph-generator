package com.depgraph.analyzer.detectors

import com.depgraph.analyzer.config.ConfigFileParser
import com.depgraph.analyzer.model.DetectedService
import com.depgraph.analyzer.plugin.ServiceDetectorPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class EnvFileDetector(
    private val configFileParser: ConfigFileParser,
) : ServiceDetectorPlugin {

    override val id = "detector.env-file"

    private val envFileNames = listOf(".env", ".env.local", ".env.development", ".env.production")

    override fun detect(projectRoot: Path): List<DetectedService> {
        val allEnvVars = mutableMapOf<String, String>()

        // Collect env vars from all .env files
        Files.walk(projectRoot, 3).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString() in envFileNames }
                .forEach { envFile ->
                    val parsed = configFileParser.parseEnvFile(envFile)
                    allEnvVars.putAll(parsed)
                    log.debug { "[$id] Parsed ${parsed.size} env vars from ${envFile.fileName}" }
                }
        }

        // Extract service names from URL env vars
        val services = allEnvVars
            .filter { (_, value) -> value.startsWith("http://") || value.startsWith("https://") }
            .mapNotNull { (key, value) -> extractServiceFromEnvVar(key, value) }
            .distinctBy { it.name }

        log.info { "[$id] Discovered ${services.size} services from .env files" }
        return services
    }

    private fun extractServiceFromEnvVar(key: String, value: String): DetectedService? {
        return try {
            val uri = URI.create(value)
            val host = uri.host ?: return null
            // Use hostname as service name (e.g. account-service from http://account-service:3000)
            val serviceName = host.split(".").first()

            DetectedService(
                id = "$id:$serviceName",
                name = serviceName,
                language = "unknown",
                framework = null,
                rootPath = serviceName,
            )
        } catch (ex: Exception) {
            log.debug { "[$id] Could not extract service from env var $key=$value: ${ex.message}" }
            null
        }
    }
}
