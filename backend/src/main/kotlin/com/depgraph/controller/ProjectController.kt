package com.depgraph.controller

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
    fun listProjects(): ResponseEntity<List<ProjectResponse>> =
        ResponseEntity.ok(projectService.findAll())

    @GetMapping("/{id}")
    fun getProject(@PathVariable id: String): ResponseEntity<ProjectResponse> =
        ResponseEntity.ok(projectService.findById(id))

    @GetMapping("/slug/{slug}")
    fun getProjectBySlug(@PathVariable slug: String): ResponseEntity<ProjectResponse> =
        ResponseEntity.ok(projectService.findBySlug(slug))

    @PostMapping
    fun createProject(@Valid @RequestBody request: CreateProjectRequest): ResponseEntity<ProjectResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request))

    @PutMapping("/{id}")
    fun updateProject(
        @PathVariable id: String,
        @RequestBody request: UpdateProjectRequest,
    ): ResponseEntity<ProjectResponse> =
        ResponseEntity.ok(projectService.update(id, request))

    @DeleteMapping("/{id}")
    fun deleteProject(@PathVariable id: String): ResponseEntity<Void> {
        projectService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/ingest")
    fun ingestProject(
        @PathVariable id: String,
        @RequestBody request: IngestRequest,
    ): ResponseEntity<Map<String, String>> {
        ingestionService.ingest(id, request)
        return ResponseEntity.accepted().body(mapOf("message" to "Ingestion started", "projectId" to id))
    }
}
