package com.depgraph.service.ingestion

import com.depgraph.domain.ProjectRepoStatus
import com.depgraph.domain.ProjectStatus
import com.depgraph.dto.IngestRequest
import com.depgraph.exception.IngestionException
import com.depgraph.exception.InvalidGitUrlException
import com.depgraph.service.ProjectRepoService
import com.depgraph.service.ProjectService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class IngestionService(
    private val projectService: ProjectService,
    private val projectRepoService: ProjectRepoService,
    private val gitCloneService: GitCloneService,
    private val zipIngestionService: ZipIngestionService,
    private val analysisOrchestrator: AnalysisOrchestrator,
) {

    @Async
    fun ingest(projectId: String, request: IngestRequest) {
        ingestSync(projectId, request)
    }

    /**
     * Synchronous ingestion — use from already-async callers (e.g. AnalyzeService).
     */
    fun ingestSync(projectId: String, request: IngestRequest, repoId: String? = null) {
        log.info { "Starting ingestion for project: $projectId, repoId: $repoId" }
        projectService.updateStatus(projectId, ProjectStatus.INGESTING)
        repoId?.let { projectRepoService.updateStatus(it, ProjectRepoStatus.INGESTING) }

        try {
            val workDir: Path = when {
                request.gitUrl != null -> {
                    validateGitUrl(request.gitUrl)
                    gitCloneService.clone(request.gitUrl, request.branch ?: "main")
                }
                else -> throw IngestionException("No ingestion source provided (gitUrl required)")
            }

            projectService.updateStatus(projectId, ProjectStatus.ANALYZING)
            repoId?.let { projectRepoService.updateStatus(it, ProjectRepoStatus.ANALYZING) }
            analysisOrchestrator.analyze(projectId, workDir, repoId)
            repoId?.let { projectRepoService.markAnalyzed(it) }
            projectService.recalculateProjectStatus(projectId)
            log.info { "Ingestion completed for project: $projectId, repoId: $repoId" }
        } catch (ex: IngestionException) {
            log.error(ex) { "Ingestion failed for project: $projectId" }
            projectService.updateStatus(projectId, ProjectStatus.ERROR)
            repoId?.let { projectRepoService.updateStatus(it, ProjectRepoStatus.ERROR) }
            throw ex
        } catch (ex: Exception) {
            log.error(ex) { "Unexpected error during ingestion for project: $projectId" }
            projectService.updateStatus(projectId, ProjectStatus.ERROR)
            repoId?.let { projectRepoService.updateStatus(it, ProjectRepoStatus.ERROR) }
            throw IngestionException("Ingestion failed: ${ex.message}", ex)
        }
    }

    /**
     * Ingest a specific repo within a project.
     */
    fun ingestRepo(projectId: String, repoId: String, gitUrl: String, branch: String?) {
        val request = IngestRequest(gitUrl = gitUrl, branch = branch ?: "main")
        ingestSync(projectId, request, repoId)
    }

    /**
     * Synchronous ZIP ingestion — extracts and analyzes a ZIP file.
     */
    fun ingestZip(projectId: String, file: org.springframework.web.multipart.MultipartFile) {
        log.info { "Starting ZIP ingestion for project: $projectId" }
        projectService.updateStatus(projectId, ProjectStatus.INGESTING)

        try {
            val workDir = zipIngestionService.extract(file)

            projectService.updateStatus(projectId, ProjectStatus.ANALYZING)
            analysisOrchestrator.analyze(projectId, workDir)
            projectService.updateStatus(projectId, ProjectStatus.READY)
            log.info { "ZIP ingestion completed for project: $projectId" }
        } catch (ex: IngestionException) {
            log.error(ex) { "ZIP ingestion failed for project: $projectId" }
            projectService.updateStatus(projectId, ProjectStatus.ERROR)
            throw ex
        } catch (ex: Exception) {
            log.error(ex) { "Unexpected error during ZIP ingestion for project: $projectId" }
            projectService.updateStatus(projectId, ProjectStatus.ERROR)
            throw IngestionException("ZIP ingestion failed: ${ex.message}", ex)
        }
    }

    private fun validateGitUrl(url: String) {
        val validPrefixes = listOf("https://", "http://", "git@", "ssh://")
        if (validPrefixes.none { url.startsWith(it) }) {
            throw InvalidGitUrlException(url)
        }
    }
}
