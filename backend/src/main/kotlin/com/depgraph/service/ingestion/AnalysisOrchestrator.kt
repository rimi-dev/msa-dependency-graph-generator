package com.depgraph.service.ingestion

import com.depgraph.service.analyzer.DependencyAnalyzer
import com.depgraph.service.analyzer.ServiceDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class AnalysisOrchestrator(
    private val serviceDetector: ServiceDetector,
    private val dependencyAnalyzer: DependencyAnalyzer,
) {

    fun analyze(projectId: String, workDir: Path) {
        log.info { "Starting analysis for project: $projectId at $workDir" }

        val detectedServices = serviceDetector.detect(projectId, workDir)
        log.info { "Detected ${detectedServices.size} services for project: $projectId" }

        dependencyAnalyzer.analyze(projectId, workDir, detectedServices)
        log.info { "Dependency analysis completed for project: $projectId" }
    }
}
