package com.depgraph.controller

import com.depgraph.dto.*
import com.depgraph.service.ProjectRepoService
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
    private val projectRepoService: ProjectRepoService,
    private val ingestionService: IngestionService,
) {

    @GetMapping
    fun listProjects(): ResponseEntity<ApiResponse<List<ProjectListResponse>>> =
        ResponseEntity.ok(ApiResponse.success(projectService.findAllForFrontend()))

    @GetMapping("/{id}")
    fun getProject(@PathVariable id: String): ResponseEntity<ApiResponse<ProjectResponse>> =
        ResponseEntity.ok(ApiResponse.success(projectService.findByIdWithRepos(id)))

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

    // --- Repo management endpoints ---

    @GetMapping("/{id}/repos")
    fun listRepos(@PathVariable id: String): ResponseEntity<ApiResponse<List<ProjectRepoResponse>>> {
        val repos = projectRepoService.findAllByProjectId(id).map { ProjectRepoResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(repos))
    }

    @PostMapping("/{id}/repos")
    fun addRepo(
        @PathVariable id: String,
        @Valid @RequestBody request: AddRepoRequest,
    ): ResponseEntity<ApiResponse<ProjectRepoResponse>> {
        val repo = projectRepoService.addRepo(id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ProjectRepoResponse.from(repo)))
    }

    @PostMapping("/{id}/repos/batch")
    fun addRepos(
        @PathVariable id: String,
        @Valid @RequestBody request: AddReposRequest,
    ): ResponseEntity<ApiResponse<List<ProjectRepoResponse>>> {
        val repos = projectRepoService.addRepos(id, request.repos).map { ProjectRepoResponse.from(it) }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(repos))
    }

    @DeleteMapping("/{id}/repos/{repoId}")
    fun removeRepo(
        @PathVariable id: String,
        @PathVariable repoId: String,
    ): ResponseEntity<ApiResponse<Unit>> {
        projectRepoService.removeRepo(id, repoId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
