package com.depgraph.dto

import java.time.Instant

data class GraphDataResponse(
    val nodes: List<ServiceNodeResponse>,
    val edges: List<DependencyEdgeResponse>,
    val metadata: GraphMetadataResponse,
)

data class ServiceNodeResponse(
    val id: String,
    val displayName: String,
    val language: String,
    val framework: String? = null,
    val dependencyCount: Int = 0,
    val repoId: String? = null,
    val repoUrl: String? = null,
)

data class DependencyEdgeResponse(
    val id: String,
    val source: String,
    val target: String,
    val protocol: String,
    val method: String? = null,
    val endpoint: String? = null,
    val confidence: Double = 0.0,
    val detectedBy: String? = null,
    val sourceLocationCount: Int = 0,
)

data class GraphMetadataResponse(
    val projectId: String,
    val projectName: String,
    val analyzedAt: Instant,
    val totalNodes: Int,
    val totalEdges: Int,
    val languages: List<String>,
)
