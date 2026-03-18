package com.depgraph.dto

import java.time.Instant

data class ProjectListResponse(
    val id: String,
    val name: String,
    val repoUrl: String? = null,
    val repoCount: Int = 0,
    val language: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nodeCount: Int = 0,
    val edgeCount: Int = 0,
)
