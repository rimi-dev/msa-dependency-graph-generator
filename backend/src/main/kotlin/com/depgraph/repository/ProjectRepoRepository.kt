package com.depgraph.repository

import com.depgraph.domain.ProjectRepo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepoRepository : JpaRepository<ProjectRepo, String> {
    fun findAllByProjectId(projectId: String): List<ProjectRepo>
    fun findByProjectIdAndGitUrl(projectId: String, gitUrl: String): ProjectRepo?
    fun existsByProjectIdAndGitUrl(projectId: String, gitUrl: String): Boolean
}
