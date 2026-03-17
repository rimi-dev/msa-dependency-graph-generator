package com.depgraph.dto

import com.depgraph.domain.AnalysisStep
import java.time.Instant

data class AnalyzeRequest(
    val repoUrl: String? = null
)

data class AnalyzeResponse(
    val jobId: String,
    val message: String
)

data class JobStatusResponse(
    val jobId: String,
    val projectId: String? = null,
    val step: AnalysisStep,
    val progress: Int,
    val message: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val error: String? = null
)
