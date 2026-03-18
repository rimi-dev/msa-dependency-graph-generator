package com.depgraph.repository

import com.depgraph.domain.Service
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRepository : JpaRepository<Service, String> {
    fun findAllByProjectId(projectId: String): List<Service>
    fun deleteAllByProjectId(projectId: String)
    fun findAllByRepoId(repoId: String): List<Service>
    fun deleteAllByRepoId(repoId: String)
}
