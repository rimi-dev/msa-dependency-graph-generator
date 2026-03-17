package com.depgraph.service

import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import com.depgraph.domain.TechStack
import com.depgraph.dto.*
import com.depgraph.exception.DependencyNotFoundException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.repository.DependencyRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GraphService(
    private val projectRepository: ProjectRepository,
    private val serviceRepository: ServiceRepository,
    private val dependencyRepository: DependencyRepository,
) {

    @Cacheable("dependency-graph", key = "#projectId")
    fun getGraph(projectId: String): DependencyGraphResponse {
        if (!projectRepository.existsById(projectId)) {
            throw ProjectNotFoundException(projectId)
        }

        val services = serviceRepository.findAllByProjectId(projectId)
            .map { ServiceResponse.from(it) }

        val edges = dependencyRepository.findAllByProjectId(projectId)
            .map { DependencyResponse.from(it) }

        return DependencyGraphResponse(
            projectId = projectId,
            nodes = services,
            edges = edges,
        )
    }

    @Cacheable("dependency-graph-frontend", key = "#projectId")
    fun getGraphForFrontend(projectId: String): GraphDataResponse {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        val services = serviceRepository.findAllByProjectId(projectId)
        val dependencies = dependencyRepository.findAllByProjectId(projectId)

        val dependencyCounts = mutableMapOf<String, Int>()
        dependencies.forEach { dep ->
            val sourceId = dep.source.id!!
            val targetId = dep.target.id!!
            dependencyCounts[sourceId] = (dependencyCounts[sourceId] ?: 0) + 1
            dependencyCounts[targetId] = (dependencyCounts[targetId] ?: 0) + 1
        }

        val nodes = services.map { svc ->
            val (language, framework) = mapTechStack(svc.techStack)
            ServiceNodeResponse(
                id = svc.id!!,
                displayName = svc.name,
                language = language,
                framework = framework,
                dependencyCount = dependencyCounts[svc.id] ?: 0,
            )
        }

        val edges = dependencies.map { dep ->
            val parsed = parseDetail(dep.detail)
            DependencyEdgeResponse(
                id = dep.id!!,
                source = dep.source.id!!,
                target = dep.target.id!!,
                protocol = mapDependencyType(dep.type),
                method = parsed["method"],
                endpoint = parsed["endpoint"],
                confidence = parsed["confidence"]?.toDoubleOrNull() ?: 0.0,
                detectedBy = parsed["detectedBy"],
                sourceLocationCount = parsed["sourceLocationCount"]?.toIntOrNull() ?: 0,
            )
        }

        val languages = nodes.map { it.language }.distinct().filter { it != "unknown" }

        return GraphDataResponse(
            nodes = nodes,
            edges = edges,
            metadata = GraphMetadataResponse(
                projectId = project.id!!,
                projectName = project.name,
                analyzedAt = project.updatedAt,
                totalNodes = nodes.size,
                totalEdges = edges.size,
                languages = languages,
            ),
        )
    }

    private fun mapTechStack(techStack: TechStack): Pair<String, String?> = when (techStack) {
        TechStack.SPRING_BOOT -> "kotlin" to "spring-boot"
        TechStack.NODE_EXPRESS -> "typescript" to "express"
        TechStack.NODE_NEST -> "typescript" to "nestjs"
        TechStack.FASTAPI -> "python" to "fastapi"
        TechStack.DJANGO -> "python" to "django"
        TechStack.RAILS -> "ruby" to "rails"
        TechStack.UNKNOWN -> "unknown" to null
    }

    private fun mapDependencyType(type: DependencyType): String = when (type) {
        DependencyType.HTTP -> "HTTP"
        DependencyType.GRPC -> "gRPC"
        DependencyType.MESSAGE_QUEUE -> "MQ"
        DependencyType.DATABASE_SHARED -> "DB"
        DependencyType.WEBSOCKET -> "WebSocket"
        DependencyType.UNKNOWN -> "Unknown"
    }

    fun getDependencySource(projectId: String, depId: String): SourceDetailResponse {
        val dependency = dependencyRepository.findById(depId)
            .orElseThrow { DependencyNotFoundException(depId) }

        val protocol = mapDependencyType(dependency.type)
        val detail = parseDetail(dependency.detail)

        return SourceDetailResponse(
            dependency = DependencySummary(
                id = dependency.id!!,
                source = dependency.source.name,
                target = dependency.target.name,
                protocol = protocol,
            ),
            locations = if (detail["endpoint"] != null) {
                listOf(
                    SourceLocationDetail(
                        filePath = "detected via static analysis",
                        startLine = 0,
                        endLine = 0,
                        content = "${detail["method"] ?: "?"} ${detail["endpoint"] ?: "?"}",
                        language = "text",
                    )
                )
            } else {
                emptyList()
            },
            relatedConfig = emptyList(),
        )
    }

    private fun parseDetail(detail: String?): Map<String, String> {
        if (detail.isNullOrBlank()) return emptyMap()

        val result = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)=(\S+)""")
        regex.findAll(detail).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }
}
