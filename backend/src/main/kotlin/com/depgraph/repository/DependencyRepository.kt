package com.depgraph.repository

import com.depgraph.domain.Dependency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DependencyRepository : JpaRepository<Dependency, String> {

    @Query(
        """
        SELECT d FROM Dependency d
        JOIN FETCH d.source s
        JOIN FETCH d.target t
        WHERE s.project.id = :projectId OR t.project.id = :projectId
        """
    )
    fun findAllByProjectId(projectId: String): List<Dependency>

    fun deleteAllBySourceProjectIdOrTargetProjectId(sourceProjectId: String, targetProjectId: String)
}
