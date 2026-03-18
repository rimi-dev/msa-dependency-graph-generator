package com.depgraph.dto

import com.depgraph.domain.Project
import com.depgraph.domain.ProjectStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

data class CreateProjectRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "Slug is required")
    @field:Pattern(
        regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
        message = "Slug must be lowercase alphanumeric with hyphens"
    )
    val slug: String,

    val description: String? = null,
    val gitUrl: String? = null,
)

data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
    val gitUrl: String? = null,
)

data class ProjectResponse(
    val id: String,
    val name: String,
    val slug: String,
    val description: String?,
    val gitUrl: String?,
    val status: ProjectStatus,
    val repos: List<ProjectRepoResponse>? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(project: Project, repos: List<ProjectRepoResponse>? = null) = ProjectResponse(
            id = project.id!!,
            name = project.name,
            slug = project.slug,
            description = project.description,
            gitUrl = project.gitUrl,
            status = project.status,
            repos = repos,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
        )
    }
}

data class IngestRequest(
    val gitUrl: String? = null,
    val branch: String? = "main",
)
