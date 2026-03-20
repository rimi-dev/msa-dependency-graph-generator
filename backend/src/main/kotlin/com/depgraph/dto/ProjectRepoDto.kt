package com.depgraph.dto

import com.depgraph.domain.ProjectRepo
import com.depgraph.domain.ProjectRepoStatus
import java.time.Instant

data class AddRepoRequest(
    val gitUrl: String,
    val branch: String? = null,
    val serviceId: String? = null,
)

data class AddReposRequest(
    val repos: List<AddRepoRequest>,
)

data class ProjectRepoResponse(
    val id: String,
    val gitUrl: String,
    val branch: String?,
    val status: ProjectRepoStatus,
    val lastAnalyzedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(repo: ProjectRepo) = ProjectRepoResponse(
            id = repo.id!!,
            gitUrl = repo.gitUrl,
            branch = repo.branch,
            status = repo.status,
            lastAnalyzedAt = repo.lastAnalyzedAt,
            createdAt = repo.createdAt,
            updatedAt = repo.updatedAt,
        )
    }
}
