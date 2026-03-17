package com.depgraph.service

import com.depgraph.domain.AnalysisStep
import com.depgraph.domain.ProjectStatus
import com.depgraph.dto.AnalyzeRequest
import com.depgraph.dto.AnalyzeResponse
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.dto.IngestRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.service.ingestion.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class AnalyzeService(
    private val projectService: ProjectService,
    private val jobService: JobService,
    private val ingestionService: IngestionService,
) {

    fun startGitAnalysis(request: AnalyzeRequest): AnalyzeResponse {
        val repoUrl = request.repoUrl ?: throw IllegalArgumentException("repoUrl is required")
        val (name, slug) = extractProjectInfo(repoUrl)

        val project = findOrCreateProject(name, slug, repoUrl)
        val job = jobService.createJob(repoUrl = repoUrl)

        runGitAnalysisAsync(project.id, job.id, repoUrl)

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

    @Async
    fun runGitAnalysisAsync(projectId: String, jobId: String, repoUrl: String) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 10, "Cloning repository...", project)

            ingestionService.ingestSync(projectId, IngestRequest(gitUrl = repoUrl))

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "Analysis completed")
        } catch (e: Exception) {
            logger.error(e) { "Analysis failed for job $jobId" }
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
