package com.depgraph.service

import com.depgraph.dto.DependencyGraphResponse
import com.depgraph.dto.DependencyResponse
import com.depgraph.dto.ServiceResponse
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
}
