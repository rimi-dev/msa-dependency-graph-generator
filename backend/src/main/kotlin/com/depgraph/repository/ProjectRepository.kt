package com.depgraph.repository

import com.depgraph.domain.Project
import com.depgraph.domain.ProjectStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProjectRepository : JpaRepository<Project, String> {
    fun findBySlug(slug: String): Optional<Project>
    fun existsBySlug(slug: String): Boolean
    fun findAllByStatus(status: ProjectStatus): List<Project>
}
