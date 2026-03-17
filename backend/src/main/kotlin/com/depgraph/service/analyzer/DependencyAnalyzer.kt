package com.depgraph.service.analyzer

import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import com.depgraph.domain.Service
import com.depgraph.repository.DependencyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class DependencyAnalyzer(
    private val dependencyRepository: DependencyRepository,
) {

    // Regex patterns to detect HTTP calls to other services
    private val httpPatterns = listOf(
        Regex("""http[s]?://[^/\s"']+"""),
        Regex("""restTemplate\..*\("([^"]+)"""),
        Regex("""webClient\..*\.uri\("([^"]+)"""),
        Regex("""axios\.(get|post|put|delete|patch)\(['"]([^'"]+)"""),
        Regex("""fetch\(['"]([^'"]+)"""),
    )

    fun analyze(projectId: String, workDir: Path, services: List<Service>): List<Dependency> {
        val serviceNames = services.associateBy { it.name.lowercase() }
        val dependencies = mutableListOf<Dependency>()

        services.forEach { source ->
            val servicePath = workDir.resolve(source.path ?: source.name)
            if (!Files.exists(servicePath)) return@forEach

            val foundTargets = scanForDependencies(servicePath, serviceNames, source.name)
            foundTargets.forEach { (targetService, type) ->
                if (targetService.id != source.id) {
                    dependencies.add(
                        Dependency(
                            source = source,
                            target = targetService,
                            type = type,
                        )
                    )
                }
            }
        }

        val saved = dependencyRepository.saveAll(dependencies)
        log.info { "Saved ${saved.size} dependencies for project: $projectId" }
        return saved
    }

    private fun scanForDependencies(
        servicePath: Path,
        serviceNames: Map<String, Service>,
        currentServiceName: String,
    ): List<Pair<Service, DependencyType>> {
        val results = mutableListOf<Pair<Service, DependencyType>>()

        Files.walk(servicePath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { isSourceFile(it) }
                .forEach { file ->
                    try {
                        val content = Files.readString(file)
                        serviceNames.forEach { (name, service) ->
                            if (name != currentServiceName.lowercase() && name in content.lowercase()) {
                                val type = detectDependencyType(content, file)
                                results.add(service to type)
                            }
                        }
                    } catch (ex: Exception) {
                        log.debug { "Could not read file: $file - ${ex.message}" }
                    }
                }
        }

        return results.distinctBy { it.first.id }
    }

    private fun isSourceFile(path: Path): Boolean {
        val name = path.fileName.toString()
        return name.endsWith(".kt") ||
            name.endsWith(".java") ||
            name.endsWith(".ts") ||
            name.endsWith(".js") ||
            name.endsWith(".py") ||
            name.endsWith(".yml") ||
            name.endsWith(".yaml") ||
            name.endsWith(".properties")
    }

    private fun detectDependencyType(content: String, @Suppress("UNUSED_PARAMETER") file: Path): DependencyType {
        return when {
            "grpc" in content.lowercase() -> DependencyType.GRPC
            "kafka" in content.lowercase() || "rabbitmq" in content.lowercase() ||
                "amqp" in content.lowercase() -> DependencyType.MESSAGE_QUEUE
            "websocket" in content.lowercase() || "stomp" in content.lowercase() -> DependencyType.WEBSOCKET
            httpPatterns.any { it.containsMatchIn(content) } -> DependencyType.HTTP
            else -> DependencyType.HTTP
        }
    }
}
