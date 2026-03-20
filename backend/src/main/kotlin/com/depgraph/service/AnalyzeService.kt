package com.depgraph.service

import com.depgraph.dto.AddRepoRequest
import com.depgraph.dto.AnalyzeRequest
import com.depgraph.dto.AnalyzeResponse
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoAlreadyExistsException
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class AnalyzeService(
    private val projectService: ProjectService,
    private val projectRepoService: ProjectRepoService,
    private val projectRepository: ProjectRepository,
    private val projectRepoRepository: ProjectRepoRepository,
    private val userRepository: UserRepository,
    private val jobService: JobService,
    private val asyncAnalysisRunner: AsyncAnalysisRunner,
) {

    private fun getCurrentUserGithubToken(): String? {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? String
            ?: return null
        return userRepository.findById(userId).orElse(null)?.githubAccessToken
    }

    fun startGitAnalysis(request: AnalyzeRequest): AnalyzeResponse {
        val repoUrl = request.repoUrl ?: throw IllegalArgumentException("repoUrl is required")
        val githubToken = getCurrentUserGithubToken()

        if (request.projectId != null) {
            val project = projectRepository.findById(request.projectId)
                .orElseThrow { ProjectNotFoundException(request.projectId) }

            val repo = findOrCreateRepo(request.projectId, repoUrl)
            val job = jobService.createJob(repoUrl = repoUrl, repo = repo)

            asyncAnalysisRunner.runRepoAnalysis(request.projectId, repo.id!!, job.id, repoUrl, githubToken = githubToken)

            return AnalyzeResponse(jobId = job.id, message = "Analysis started for repo in project ${project.name}")
        }

        val (name, slug) = extractProjectInfo(repoUrl)
        val project = findOrCreateProject(name, slug, repoUrl)
        val repo = findOrCreateRepo(project.id, repoUrl)
        val job = jobService.createJob(repoUrl = repoUrl, repo = repo)

        asyncAnalysisRunner.runRepoAnalysis(project.id, repo.id!!, job.id, repoUrl, githubToken = githubToken)

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for $name")
    }

    fun startZipAnalysis(file: MultipartFile): AnalyzeResponse {
        val fileName = file.originalFilename?.removeSuffix(".zip") ?: "uploaded-project"
        val slug = fileName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        // @Async에서 MultipartFile 접근 불가 — 임시 파일로 복사
        val tempZip = java.nio.file.Files.createTempFile("depgraph_upload_", ".zip")
        file.transferTo(tempZip)

        val project = findOrCreateProject(fileName, slug)
        val job = jobService.createJob()

        asyncAnalysisRunner.runZipAnalysis(project.id, job.id, tempZip)

        return AnalyzeResponse(jobId = job.id, message = "ZIP analysis started for $fileName")
    }

    fun analyzeAllRepos(projectId: String): AnalyzeResponse {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }
        val githubToken = getCurrentUserGithubToken()

        val repos = projectRepoService.findAllByProjectId(projectId)
        if (repos.isEmpty()) {
            throw IllegalStateException("No repos registered for project: ${project.name}")
        }

        val job = jobService.createJob()

        asyncAnalysisRunner.runAllReposAnalysis(projectId, job.id, repos.map { Triple(it.id!!, it.gitUrl, it.branch) }, githubToken)

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for all ${repos.size} repos in ${project.name}")
    }

    fun analyzeSingleRepo(projectId: String, repoId: String): AnalyzeResponse {
        val repo = projectRepoService.findById(repoId)
        val githubToken = getCurrentUserGithubToken()
        val job = jobService.createJob(repoUrl = repo.gitUrl, repo = repo)

        asyncAnalysisRunner.runRepoAnalysis(projectId, repoId, job.id, repo.gitUrl, repo.branch, githubToken)

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for repo ${repo.gitUrl}")
    }

    private fun findOrCreateProject(name: String, slug: String, gitUrl: String? = null): com.depgraph.dto.ProjectResponse {
        return try {
            projectService.create(CreateProjectRequest(name = name, slug = slug, gitUrl = gitUrl))
        } catch (e: ProjectAlreadyExistsException) {
            projectService.findBySlug(slug)
        }
    }

    private fun findOrCreateRepo(projectId: String, gitUrl: String): com.depgraph.domain.ProjectRepo {
        return try {
            projectRepoService.addRepo(projectId, AddRepoRequest(gitUrl = gitUrl))
        } catch (e: ProjectRepoAlreadyExistsException) {
            projectRepoRepository.findByProjectIdAndGitUrl(projectId, gitUrl)!!
        }
    }

    private fun extractProjectInfo(repoUrl: String): Pair<String, String> {
        val name = repoUrl
            .removeSuffix(".git")
            .split("/", ":")
            .last()
            .ifEmpty { "unknown-project" }
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return Pair(name, slug)
    }
}
