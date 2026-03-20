package com.depgraph.service

import com.depgraph.domain.ProjectRepo
import com.depgraph.domain.ProjectRepoStatus
import com.depgraph.dto.AddRepoRequest
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoAlreadyExistsException
import com.depgraph.exception.ProjectRepoNotFoundException
import com.depgraph.exception.ServiceNotFoundException
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
@Transactional
class ProjectRepoService(
    private val projectRepoRepository: ProjectRepoRepository,
    private val projectRepository: ProjectRepository,
    private val serviceRepository: ServiceRepository,
) {

    @Transactional(readOnly = true)
    fun findAllByProjectId(projectId: String): List<ProjectRepo> {
        return projectRepoRepository.findAllByProjectId(projectId)
    }

    fun addRepo(projectId: String, request: AddRepoRequest): ProjectRepo {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        if (projectRepoRepository.existsByProjectIdAndGitUrl(projectId, request.gitUrl)) {
            throw ProjectRepoAlreadyExistsException(request.gitUrl)
        }

        val repo = ProjectRepo(
            project = project,
            gitUrl = request.gitUrl,
            branch = request.branch,
        )
        log.info { "Adding repo ${request.gitUrl} to project $projectId" }
        val savedRepo = projectRepoRepository.save(repo)

        // 서비스에 레포 연결
        request.serviceId?.let { serviceId ->
            val service = serviceRepository.findById(serviceId)
                .orElseThrow { ServiceNotFoundException(serviceId) }
            if (service.project.id == projectId) {
                serviceRepository.save(service.copy(repo = savedRepo, updatedAt = Instant.now()))
                log.info { "Linked repo ${savedRepo.id} to service $serviceId" }
            }
        }

        return savedRepo
    }

    fun addRepos(projectId: String, requests: List<AddRepoRequest>): List<ProjectRepo> {
        return requests.map { addRepo(projectId, it) }
    }

    fun removeRepo(projectId: String, repoId: String) {
        val repo = projectRepoRepository.findById(repoId)
            .orElseThrow { ProjectRepoNotFoundException(repoId) }
        if (repo.project.id != projectId) {
            throw ProjectRepoNotFoundException(repoId)
        }
        serviceRepository.deleteAllByRepoId(repoId)
        projectRepoRepository.delete(repo)
        log.info { "Removed repo $repoId from project $projectId" }
    }

    fun updateStatus(repoId: String, status: ProjectRepoStatus): ProjectRepo {
        val repo = projectRepoRepository.findById(repoId)
            .orElseThrow { ProjectRepoNotFoundException(repoId) }
        val updated = repo.copy(status = status, updatedAt = Instant.now())
        return projectRepoRepository.save(updated)
    }

    fun markAnalyzed(repoId: String): ProjectRepo {
        val repo = projectRepoRepository.findById(repoId)
            .orElseThrow { ProjectRepoNotFoundException(repoId) }
        val updated = repo.copy(
            status = ProjectRepoStatus.READY,
            lastAnalyzedAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        return projectRepoRepository.save(updated)
    }

    @Transactional(readOnly = true)
    fun findById(repoId: String): ProjectRepo {
        return projectRepoRepository.findById(repoId)
            .orElseThrow { ProjectRepoNotFoundException(repoId) }
    }
}
