package com.depgraph.controller

import com.depgraph.dto.ApiResponse
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.dto.IngestRequest
import com.depgraph.dto.ProjectResponse
import com.depgraph.dto.UpdateProjectRequest
import com.depgraph.service.ProjectService
import com.depgraph.service.ingestion.IngestionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val projectService: ProjectService,
    private val ingestionService: IngestionService,
) {

    @GetMapping
    fun listProjects(): ResponseEntity<ApiResponse<List<ProjectResponse>>> =
        ResponseEntity.ok(ApiResponse.success(projectService.findAll()))

    @GetMapping("/{id}")
    fun getProject(@PathVariable id: String): ResponseEntity<ApiResponse<ProjectResponse>> =
        ResponseEntity.ok(ApiResponse.success(projectService.findById(id)))

    @GetMapping("/slug/{slug}")
    fun getProjectBySlug(@PathVariable slug: String): ResponseEntity<ApiResponse<ProjectResponse>> =
        ResponseEntity.ok(ApiResponse.success(projectService.findBySlug(slug)))

    @PostMapping
    fun createProject(@Valid @RequestBody request: CreateProjectRequest): ResponseEntity<ApiResponse<ProjectResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(projectService.create(request)))

    @PutMapping("/{id}")
    fun updateProject(
        @PathVariable id: String,
        @RequestBody request: UpdateProjectRequest,
    ): ResponseEntity<ApiResponse<ProjectResponse>> =
        ResponseEntity.ok(ApiResponse.success(projectService.update(id, request)))

    @DeleteMapping("/{id}")
    fun deleteProject(@PathVariable id: String): ResponseEntity<ApiResponse<Unit>> {
        projectService.delete(id)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @PostMapping("/{id}/ingest")
    fun ingestProject(
        @PathVariable id: String,
        @RequestBody request: IngestRequest,
    ): ResponseEntity<ApiResponse<ProjectResponse>> {
        ingestionService.ingest(id, request)
        val project = projectService.findById(id)
        return ResponseEntity.accepted().body(ApiResponse.success(project))
    }
}
