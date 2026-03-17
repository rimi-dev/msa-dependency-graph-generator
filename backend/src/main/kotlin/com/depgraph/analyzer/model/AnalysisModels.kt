package com.depgraph.analyzer.model

import java.nio.file.Path

data class AnalysisContext(
    val projectRoot: Path,
    val files: List<SourceFile>,
    val configFiles: Map<String, Any>,
    val envVariables: Map<String, String>,
)

data class SourceFile(
    val path: Path,
    val relativePath: String,
    val content: String,
    val language: String,
)

data class DetectedDependency(
    val source: String,
    val target: String,
    val protocol: String,
    val method: String?,
    val endpoint: String?,
    val confidence: Double,
    val detectedBy: String,
    val sourceLocations: List<SourceLocation>,
)

data class DetectedService(
    val id: String,
    val name: String,
    val language: String,
    val framework: String?,
    val rootPath: String,
)

data class SourceLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val snippet: String,
)
