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

        // 모든 .env 파일에서 환경변수 수집
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

        // URL 환경변수에서 서비스명 추출
        val services = allEnvVars
            .filter { (_, value) -> value.startsWith("http://") || value.startsWith("https://") }
            .mapNotNull { (key, value) -> extractServiceFromEnvVar(key, value) }
            .distinctBy { it.name }

        log.info { "[$id] Discovered ${services.size} services from .env files" }
        return services
    }

    private val IGNORED_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")

    private fun extractServiceFromEnvVar(key: String, value: String): DetectedService? {
        return try {
            val uri = URI.create(value)
            val host = uri.host ?: return null
            if (host in IGNORED_HOSTS) return null
            // 호스트명을 서비스명으로 사용 (예: http://account-service:3000에서 account-service)
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
