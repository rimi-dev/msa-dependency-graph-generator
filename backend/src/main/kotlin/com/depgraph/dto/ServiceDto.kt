package com.depgraph.dto

import com.depgraph.domain.Service
import com.depgraph.domain.TechStack
import java.time.Instant

data class ServiceResponse(
    val id: String,
    val projectId: String,
    val name: String,
    val path: String?,
    val techStack: TechStack,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(service: Service) = ServiceResponse(
            id = service.id,
            projectId = service.project.id,
            name = service.name,
            path = service.path,
            techStack = service.techStack,
            createdAt = service.createdAt,
            updatedAt = service.updatedAt,
        )
    }
}
