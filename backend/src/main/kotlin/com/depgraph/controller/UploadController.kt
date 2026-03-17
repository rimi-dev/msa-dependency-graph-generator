package com.depgraph.controller

import com.depgraph.dto.ApiResponse
import com.depgraph.dto.ProjectResponse
import com.depgraph.exception.IngestionException
import com.depgraph.service.ingestion.AnalysisOrchestrator
import com.depgraph.service.ingestion.ZipIngestionService
import com.depgraph.service.ProjectService
import com.depgraph.domain.ProjectStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/upload")
class UploadController(
    private val projectService: ProjectService,
    private val zipIngestionService: ZipIngestionService,
    private val analysisOrchestrator: AnalysisOrchestrator,
) {

    @PostMapping("/zip")
    fun uploadZip(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<ProjectResponse>> {
        if (file.isEmpty) {
            throw IngestionException("Uploaded file is empty")
        }
        if (!file.originalFilename.orEmpty().endsWith(".zip")) {
            throw IngestionException("Only ZIP files are supported")
        }

        log.info { "Received ZIP upload for project: $projectId (${file.size} bytes)" }
        projectService.updateStatus(projectId, ProjectStatus.INGESTING)

        val workDir = zipIngestionService.extract(file)

        projectService.updateStatus(projectId, ProjectStatus.ANALYZING)
        analysisOrchestrator.analyze(projectId, workDir)
        projectService.updateStatus(projectId, ProjectStatus.READY)

        val project = projectService.findById(projectId)
        return ResponseEntity.accepted().body(ApiResponse.success(project))
    }
}
