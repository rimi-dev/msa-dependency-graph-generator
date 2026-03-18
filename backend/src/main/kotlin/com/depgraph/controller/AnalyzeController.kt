package com.depgraph.controller

import com.depgraph.dto.AnalyzeRequest
import com.depgraph.dto.AnalyzeResponse
import com.depgraph.dto.ApiResponse
import com.depgraph.dto.JobStatusResponse
import com.depgraph.service.AnalyzeService
import com.depgraph.service.JobService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1")
class AnalyzeController(
    private val analyzeService: AnalyzeService,
    private val jobService: JobService,
) {

    @PostMapping("/analyze", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun analyzeFromGit(@RequestBody request: AnalyzeRequest): ResponseEntity<ApiResponse<AnalyzeResponse>> {
        val response = analyzeService.startGitAnalysis(request)
        return ResponseEntity.accepted().body(ApiResponse.success(response))
    }

    @PostMapping("/analyze", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun analyzeFromZip(@RequestParam("file") file: MultipartFile): ResponseEntity<ApiResponse<AnalyzeResponse>> {
        val response = analyzeService.startZipAnalysis(file)
        return ResponseEntity.accepted().body(ApiResponse.success(response))
    }

    @PostMapping("/projects/{projectId}/analyze")
    fun analyzeAllRepos(@PathVariable projectId: String): ResponseEntity<ApiResponse<AnalyzeResponse>> {
        val response = analyzeService.analyzeAllRepos(projectId)
        return ResponseEntity.accepted().body(ApiResponse.success(response))
    }

    @PostMapping("/projects/{projectId}/repos/{repoId}/analyze")
    fun analyzeSingleRepo(
        @PathVariable projectId: String,
        @PathVariable repoId: String,
    ): ResponseEntity<ApiResponse<AnalyzeResponse>> {
        val response = analyzeService.analyzeSingleRepo(projectId, repoId)
        return ResponseEntity.accepted().body(ApiResponse.success(response))
    }

    @GetMapping("/jobs/{jobId}")
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<ApiResponse<JobStatusResponse>> {
        val status = jobService.getJobStatus(jobId)
        return ResponseEntity.ok(ApiResponse.success(status))
    }
}
