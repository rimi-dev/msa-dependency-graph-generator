package com.depgraph.service

import com.depgraph.domain.Project
import com.depgraph.domain.ProjectRepoStatus
import com.depgraph.domain.ProjectStatus
import com.depgraph.domain.TechStack
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.dto.ProjectListResponse
import com.depgraph.dto.ProjectRepoResponse
import com.depgraph.dto.ProjectResponse
import com.depgraph.dto.UpdateProjectRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.repository.DependencyRepository
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val serviceRepository: ServiceRepository,
    private val dependencyRepository: DependencyRepository,
    private val projectRepoRepository: ProjectRepoRepository,
) {

    fun findAll(): List<ProjectResponse> =
        projectRepository.findAll().map { ProjectResponse.from(it) }

    fun findAllForFrontend(): List<ProjectListResponse> {
        val projects = projectRepository.findAll()
        return projects.map { project ->
            val projectId = project.id!!
            val services = serviceRepository.findAllByProjectId(projectId)
            val dependencies = dependencyRepository.findAllByProjectId(projectId)
            val primaryLanguage = determinePrimaryLanguage(services.map { it.techStack })

            val repos = projectRepoRepository.findAllByProjectId(projectId)

            ProjectListResponse(
                id = projectId,
                name = project.name,
                repoUrl = project.gitUrl,
                repoCount = repos.size,
                language = primaryLanguage,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                nodeCount = services.size,
                edgeCount = dependencies.size,
            )
        }
    }

    private fun determinePrimaryLanguage(techStacks: List<TechStack>): String? {
        if (techStacks.isEmpty()) return null
        val languageCounts = techStacks
            .map { mapTechStackToLanguage(it) }
            .filter { it != "unknown" }
            .groupingBy { it }
            .eachCount()
        return languageCounts.maxByOrNull { it.value }?.key
    }

    private fun mapTechStackToLanguage(techStack: TechStack): String = when (techStack) {
        TechStack.SPRING_BOOT -> "kotlin"
        TechStack.NODE_EXPRESS, TechStack.NODE_NEST -> "typescript"
        TechStack.FASTAPI, TechStack.DJANGO -> "python"
        TechStack.RAILS -> "ruby"
        TechStack.UNKNOWN -> "unknown"
    }

    fun findByIdWithRepos(id: String): ProjectResponse {
        val project = projectRepository.findById(id)
            .orElseThrow { ProjectNotFoundException(id) }
        val repos = projectRepoRepository.findAllByProjectId(id)
            .map { ProjectRepoResponse.from(it) }
        return ProjectResponse.from(project, repos)
    }

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
    fun recalculateProjectStatus(projectId: String): Project {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }
        val repos = projectRepoRepository.findAllByProjectId(projectId)

        val newStatus = when {
            repos.isEmpty() -> project.status
            repos.all { it.status == ProjectRepoStatus.READY } -> ProjectStatus.READY
            repos.any { it.status == ProjectRepoStatus.ERROR } -> ProjectStatus.ERROR
            repos.any { it.status in listOf(ProjectRepoStatus.INGESTING, ProjectRepoStatus.ANALYZING) } -> ProjectStatus.ANALYZING
            else -> ProjectStatus.PENDING
        }

        return if (newStatus != project.status) {
            projectRepository.save(project.copy(status = newStatus, updatedAt = Instant.now()))
                .also { log.info { "Recalculated project status: $projectId → $newStatus" } }
        } else {
            project
        }
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
