package com.depgraph.controller

import com.depgraph.dto.AnalyzeResponse
import com.depgraph.dto.ApiResponse
import com.depgraph.exception.IngestionException
import com.depgraph.service.AsyncAnalysisRunner
import com.depgraph.service.JobService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/upload")
class UploadController(
    private val jobService: JobService,
    private val asyncAnalysisRunner: AsyncAnalysisRunner,
) {

    @PostMapping("/zip")
    fun uploadZip(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<AnalyzeResponse>> {
        if (file.isEmpty) {
            throw IngestionException("업로드된 파일이 비어있습니다")
        }
        if (!file.originalFilename.orEmpty().endsWith(".zip")) {
            throw IngestionException("ZIP 파일만 지원됩니다")
        }

        log.info { "ZIP 업로드 수신: project=$projectId (${file.size} bytes)" }

        // @Async에서 MultipartFile 접근 불가 — 임시 파일로 복사
        val tempZip = Files.createTempFile("depgraph_upload_", ".zip")
        file.transferTo(tempZip)

        val job = jobService.createJob()
        asyncAnalysisRunner.runZipAnalysisForProject(projectId, job.id, tempZip)

        return ResponseEntity.accepted().body(
            ApiResponse.success(
                AnalyzeResponse(
                    jobId = job.id,
                    message = "ZIP 분석 시작 (project: $projectId)",
                ),
            ),
        )
    }
}
