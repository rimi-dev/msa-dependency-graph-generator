package com.depgraph.dto

import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import java.time.Instant

data class DependencyResponse(
    val id: String,
    val sourceServiceId: String,
    val sourceServiceName: String,
    val targetServiceId: String,
    val targetServiceName: String,
    val type: DependencyType,
    val detail: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(dependency: Dependency) = DependencyResponse(
            id = dependency.id,
            sourceServiceId = dependency.source.id,
            sourceServiceName = dependency.source.name,
            targetServiceId = dependency.target.id,
            targetServiceName = dependency.target.name,
            type = dependency.type,
            detail = dependency.detail,
            createdAt = dependency.createdAt,
        )
    }
}

data class DependencyGraphResponse(
    val projectId: String,
    val nodes: List<ServiceResponse>,
    val edges: List<DependencyResponse>,
)
