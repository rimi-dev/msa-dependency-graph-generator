package com.depgraph.dto

data class SourceDetailResponse(
    val dependency: DependencySummary,
    val locations: List<SourceLocationDetail>,
    val relatedConfig: List<ConfigEntry>,
)

data class DependencySummary(
    val id: String,
    val source: String,
    val target: String,
    val protocol: String,
)

data class SourceLocationDetail(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val language: String,
    val githubUrl: String? = null,
    val highlightLines: List<Int> = emptyList(),
)

data class ConfigEntry(
    val filePath: String,
    val key: String,
    val value: String,
    val githubUrl: String? = null,
)
