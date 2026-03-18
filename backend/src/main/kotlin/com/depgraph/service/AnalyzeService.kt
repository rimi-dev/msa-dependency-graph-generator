package com.depgraph.service

import com.depgraph.domain.AnalysisStep
import com.depgraph.domain.ProjectStatus
import com.depgraph.dto.AddRepoRequest
import com.depgraph.dto.AnalyzeRequest
import com.depgraph.dto.AnalyzeResponse
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.dto.IngestRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoAlreadyExistsException
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.service.ingestion.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class AnalyzeService(
    private val projectService: ProjectService,
    private val projectRepoService: ProjectRepoService,
    private val projectRepository: ProjectRepository,
    private val projectRepoRepository: ProjectRepoRepository,
    private val jobService: JobService,
    private val ingestionService: IngestionService,
) {

    fun startGitAnalysis(request: AnalyzeRequest): AnalyzeResponse {
        val repoUrl = request.repoUrl ?: throw IllegalArgumentException("repoUrl is required")

        // If projectId is provided, add repo to existing project and analyze
        if (request.projectId != null) {
            val project = projectRepository.findById(request.projectId)
                .orElseThrow { ProjectNotFoundException(request.projectId) }

            val repo = findOrCreateRepo(request.projectId, repoUrl)
            val job = jobService.createJob(repoUrl = repoUrl, repo = repo)

            runRepoAnalysisAsync(request.projectId, repo.id!!, job.id, repoUrl)

            return AnalyzeResponse(jobId = job.id, message = "Analysis started for repo in project ${project.name}")
        }

        // Default: create project + repo (backward compatible)
        val (name, slug) = extractProjectInfo(repoUrl)
        val project = findOrCreateProject(name, slug, repoUrl)
        val repo = findOrCreateRepo(project.id, repoUrl)
        val job = jobService.createJob(repoUrl = repoUrl, repo = repo)

        runRepoAnalysisAsync(project.id, repo.id!!, job.id, repoUrl)

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for $name")
    }

    fun startZipAnalysis(file: MultipartFile): AnalyzeResponse {
        val fileName = file.originalFilename?.removeSuffix(".zip") ?: "uploaded-project"
        val slug = fileName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        val project = findOrCreateProject(fileName, slug)
        val job = jobService.createJob()

        runZipAnalysisAsync(project.id, job.id, file)

        return AnalyzeResponse(jobId = job.id, message = "ZIP analysis started for $fileName")
    }

    /**
     * Analyze all repos in a project sequentially.
     */
    fun analyzeAllRepos(projectId: String): AnalyzeResponse {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        val repos = projectRepoService.findAllByProjectId(projectId)
        if (repos.isEmpty()) {
            throw IllegalStateException("No repos registered for project: ${project.name}")
        }

        val job = jobService.createJob()

        runAllReposAnalysisAsync(projectId, job.id, repos.map { Triple(it.id!!, it.gitUrl, it.branch) })

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for all ${repos.size} repos in ${project.name}")
    }

    /**
     * Analyze a single repo within a project.
     */
    fun analyzeSingleRepo(projectId: String, repoId: String): AnalyzeResponse {
        val repo = projectRepoService.findById(repoId)
        val job = jobService.createJob(repoUrl = repo.gitUrl, repo = repo)

        runRepoAnalysisAsync(projectId, repoId, job.id, repo.gitUrl, repo.branch)

        return AnalyzeResponse(jobId = job.id, message = "Analysis started for repo ${repo.gitUrl}")
    }

    @Async
    fun runRepoAnalysisAsync(projectId: String, repoId: String, jobId: String, repoUrl: String, branch: String? = null) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 10, "Cloning repository...", project)

            ingestionService.ingestRepo(projectId, repoId, repoUrl, branch)

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "Analysis completed")
        } catch (e: Exception) {
            logger.error(e) { "Analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
    }

    @Async
    fun runAllReposAnalysisAsync(projectId: String, jobId: String, repos: List<Triple<String, String, String?>>) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 5, "Starting multi-repo analysis...", project)

            val totalRepos = repos.size
            repos.forEachIndexed { index, (repoId, gitUrl, branch) ->
                val progress = 10 + (80 * index / totalRepos)
                jobService.updateJobStep(jobId, AnalysisStep.ANALYZING, progress, "Analyzing repo ${index + 1}/$totalRepos: $gitUrl")
                ingestionService.ingestRepo(projectId, repoId, gitUrl, branch)
            }

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "All repos analyzed")
        } catch (e: Exception) {
            logger.error(e) { "Multi-repo analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
    }

    @Async
    fun runZipAnalysisAsync(projectId: String, jobId: String, file: MultipartFile) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 10, "Extracting ZIP file...", project)

            ingestionService.ingestZip(projectId, file)

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "Analysis completed")
        } catch (e: Exception) {
            logger.error(e) { "ZIP analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
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
