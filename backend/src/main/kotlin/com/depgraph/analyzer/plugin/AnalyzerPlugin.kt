package com.depgraph.analyzer.plugin

import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.model.DetectedService
import java.nio.file.Path

interface AnalyzerPlugin {
    val id: String
    val name: String
    val supportedLanguages: List<String>
    val supportedFrameworks: List<String>
    fun analyze(context: AnalysisContext): List<DetectedDependency>
}

interface ServiceDetectorPlugin {
    val id: String
    fun detect(projectRoot: Path): List<DetectedService>
}
