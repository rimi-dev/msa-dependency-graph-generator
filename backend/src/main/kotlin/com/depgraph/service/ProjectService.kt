package com.depgraph.service

import com.depgraph.domain.Project
import com.depgraph.domain.ProjectStatus
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.dto.ProjectResponse
import com.depgraph.dto.UpdateProjectRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.repository.ProjectRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class ProjectService(
    private val projectRepository: ProjectRepository,
) {

    fun findAll(): List<ProjectResponse> =
        projectRepository.findAll().map { ProjectResponse.from(it) }

    fun findById(id: String): ProjectResponse =
        projectRepository.findById(id)
            .map { ProjectResponse.from(it) }
            .orElseThrow { ProjectNotFoundException(id) }

    fun findBySlug(slug: String): ProjectResponse =
        projectRepository.findBySlug(slug)
            .map { ProjectResponse.from(it) }
            .orElseThrow { ProjectNotFoundException(slug) }

    @Transactional
    fun create(request: CreateProjectRequest): ProjectResponse {
        if (projectRepository.existsBySlug(request.slug)) {
            throw ProjectAlreadyExistsException(request.slug)
        }

        val project = Project(
            name = request.name,
            slug = request.slug,
            description = request.description,
            gitUrl = request.gitUrl,
        )

        return projectRepository.save(project)
            .let { ProjectResponse.from(it) }
            .also { log.info { "Created project: ${it.id} (${it.slug})" } }
    }

    @Transactional
    fun update(id: String, request: UpdateProjectRequest): ProjectResponse {
        val project = projectRepository.findById(id)
            .orElseThrow { ProjectNotFoundException(id) }

        val updated = project.copy(
            name = request.name ?: project.name,
            description = request.description ?: project.description,
            gitUrl = request.gitUrl ?: project.gitUrl,
            updatedAt = Instant.now(),
        )

        return projectRepository.save(updated)
            .let { ProjectResponse.from(it) }
            .also { log.info { "Updated project: $id" } }
    }

    @Transactional
    fun updateStatus(id: String, status: ProjectStatus): Project {
        val project = projectRepository.findById(id)
            .orElseThrow { ProjectNotFoundException(id) }

        return projectRepository.save(project.copy(status = status, updatedAt = Instant.now()))
    }

    @Transactional
    fun delete(id: String) {
        if (!projectRepository.existsById(id)) {
            throw ProjectNotFoundException(id)
        }
        projectRepository.deleteById(id)
        log.info { "Deleted project: $id" }
    }
}
