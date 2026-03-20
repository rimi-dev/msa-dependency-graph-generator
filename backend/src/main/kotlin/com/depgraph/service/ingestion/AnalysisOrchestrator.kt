package com.depgraph.service.ingestion

import com.depgraph.analyzer.registry.AnalyzerRegistry
import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import com.depgraph.domain.Service
import com.depgraph.domain.TechStack
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoNotFoundException
import com.depgraph.repository.DependencyRepository
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import com.depgraph.service.analyzer.DependencyAnalyzer
import com.depgraph.service.analyzer.ServiceDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@SpringService
class AnalysisOrchestrator(
    private val serviceDetector: ServiceDetector,
    private val dependencyAnalyzer: DependencyAnalyzer,
    private val analyzerRegistry: AnalyzerRegistry,
    private val projectRepository: ProjectRepository,
    private val projectRepoRepository: ProjectRepoRepository,
    private val serviceRepository: ServiceRepository,
    private val dependencyRepository: DependencyRepository,
) {

    @Transactional
    fun analyze(projectId: String, workDir: Path, repoId: String? = null) {
        log.info { "Starting analysis for project: $projectId, repoId: $repoId at $workDir" }

        val repo = repoId?.let {
            projectRepoRepository.findById(it)
                .orElseThrow { ProjectRepoNotFoundException(it) }
        }

        // Phase 1: legacy service detection (saves to DB, per-repo aware)
        val detectedServices = serviceDetector.detect(projectId, workDir, repoId)
        log.info { "Detected ${detectedServices.size} services via legacy detector for project: $projectId" }

        // Phase 2: enhanced service detection via plugin registry
        val pluginDetectedServices = analyzerRegistry.detectServices(workDir)
        log.info { "Plugin registry detected ${pluginDetectedServices.size} additional services for project: $projectId" }

        // Merge plugin-detected services with legacy detected services
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        val existingServiceNames = detectedServices.map { it.name.lowercase() }.toSet()
        val newServices = pluginDetectedServices
            .filter { it.name.lowercase() !in existingServiceNames }
            .map { detected ->
                Service(
                    project = project,
                    repo = repo,
                    name = detected.name,
                    path = detected.rootPath.removePrefix(workDir.toString()).trimStart('/'),
                    techStack = mapFrameworkToTechStack(detected.framework, detected.language),
                )
            }

        val savedNewServices: List<com.depgraph.domain.Service> = if (newServices.isNotEmpty()) {
            serviceRepository.saveAll(newServices).toList()
                .also { log.info { "Saved ${it.size} newly detected services for project: $projectId" } }
        } else emptyList()

        // For dependency analysis: use ALL project services (cross-repo)
        val allProjectServices = serviceRepository.findAllByProjectId(projectId)

        // Delete all project dependencies and re-analyze (cross-repo dependencies)
        dependencyRepository.deleteAllBySourceProjectIdOrTargetProjectId(projectId, projectId)

        // Phase 3: legacy dependency analysis with all project services
        dependencyAnalyzer.analyze(projectId, workDir, allProjectServices)
        log.info { "Legacy dependency analysis completed for project: $projectId" }

        // Phase 4: enhanced plugin-based dependency analysis
        val servicesByName = allProjectServices.associateBy { it.name.lowercase() }
        val allDependencies = mutableListOf<Dependency>()

        allProjectServices.forEach { sourceService ->
            val servicePath = workDir.resolve(sourceService.path ?: sourceService.name)
            val pluginDependencies = analyzerRegistry.analyzeDependencies(servicePath, sourceService.name)

            pluginDependencies.forEach { detected ->
                val targetService = servicesByName[detected.target.lowercase()] ?: return@forEach
                if (targetService.id == sourceService.id) return@forEach

                allDependencies.add(
                    Dependency(
                        source = sourceService,
                        target = targetService,
                        type = mapProtocolToDependencyType(detected.protocol),
                        detail = buildDetail(detected),
                    ),
                )
            }
        }

        if (allDependencies.isNotEmpty()) {
            dependencyRepository.saveAll(allDependencies)
            log.info { "Saved ${allDependencies.size} plugin-detected dependencies for project: $projectId" }
        }

        log.info { "Full analysis completed for project: $projectId" }
    }

    private fun mapFrameworkToTechStack(framework: String?, language: String): TechStack {
        return when {
            framework == "nestjs" || framework == "nestjs-lib" -> TechStack.NODE_NEST
            framework == "express" || framework == "fastify" || framework == "koa" -> TechStack.NODE_EXPRESS
            language == "typescript" || language == "javascript" -> TechStack.NODE_EXPRESS
            language == "python" -> TechStack.FASTAPI
            language == "java" || language == "kotlin" -> TechStack.SPRING_BOOT
            else -> TechStack.UNKNOWN
        }
    }

    private fun mapProtocolToDependencyType(protocol: String): DependencyType {
        return when (protocol.uppercase()) {
            "HTTP", "HTTPS" -> DependencyType.HTTP
            "GRPC" -> DependencyType.GRPC
            "MQ", "AMQP", "KAFKA", "MESSAGE_QUEUE" -> DependencyType.MESSAGE_QUEUE
            "WEBSOCKET", "WS" -> DependencyType.WEBSOCKET
            else -> DependencyType.UNKNOWN
        }
    }

    private fun buildDetail(detected: com.depgraph.analyzer.model.DetectedDependency): String {
        return buildString {
            detected.method?.let { append("method=$it ") }
            detected.endpoint?.let { append("endpoint=$it ") }
            append("confidence=${detected.confidence} ")
            append("detectedBy=${detected.detectedBy}")
        }.trim()
    }
}
