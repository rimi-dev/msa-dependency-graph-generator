package com.depgraph.analyzer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class ConfigFileParser {

    private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        findAndRegisterModules()
    }
    private val jsonMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }

    private val envFilePattern = Regex(
        """^([A-Z_]+(?:URL|HOST|ENDPOINT)[A-Z_]*)\s*=\s*(.+)$""",
        RegexOption.MULTILINE,
    )

    fun parseDockerCompose(path: Path): Map<String, Any> {
        return try {
            yamlMapper.readValue<Map<String, Any>>(path.toFile())
        } catch (ex: Exception) {
            log.debug { "Failed to parse docker-compose at $path: ${ex.message}" }
            emptyMap()
        }
    }

    fun parsePackageJson(path: Path): Map<String, Any> {
        return try {
            jsonMapper.readValue<Map<String, Any>>(path.toFile())
        } catch (ex: Exception) {
            log.debug { "Failed to parse package.json at $path: ${ex.message}" }
            emptyMap()
        }
    }

    fun parseNestCliJson(path: Path): Map<String, Any> {
        return try {
            jsonMapper.readValue<Map<String, Any>>(path.toFile())
        } catch (ex: Exception) {
            log.debug { "Failed to parse nest-cli.json at $path: ${ex.message}" }
            emptyMap()
        }
    }

    fun parseEnvFile(path: Path): Map<String, String> {
        return try {
            val content = Files.readString(path)
            envFilePattern.findAll(content)
                .associate { match -> match.groupValues[1] to match.groupValues[2].trim() }
        } catch (ex: Exception) {
            log.debug { "Failed to parse .env file at $path: ${ex.message}" }
            emptyMap()
        }
    }

    fun extractDockerComposeServices(parsed: Map<String, Any>): Map<String, Map<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        return (parsed["services"] as? Map<String, Any>)
            ?.mapValues { (_, v) -> v as? Map<String, Any> ?: emptyMap() }
            ?: emptyMap()
    }

    fun extractDockerComposeEnvVars(parsed: Map<String, Any>): Map<String, String> {
        val services = extractDockerComposeServices(parsed)
        val envVars = mutableMapOf<String, String>()

        services.values.forEach { service ->
            @Suppress("UNCHECKED_CAST")
            val environment = service["environment"]
            when (environment) {
                is Map<*, *> -> environment.forEach { (k, v) ->
                    if (k is String && v is String) envVars[k] = v
                }
                is List<*> -> environment.filterIsInstance<String>().forEach { entry ->
                    val parts = entry.split("=", limit = 2)
                    if (parts.size == 2) envVars[parts[0]] = parts[1]
                }
            }
        }

        return envVars
    }
}
