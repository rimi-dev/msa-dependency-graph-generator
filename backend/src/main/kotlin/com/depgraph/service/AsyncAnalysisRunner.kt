package com.depgraph.service

import com.depgraph.domain.AnalysisStep
import com.depgraph.domain.ProjectStatus
import com.depgraph.service.ingestion.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class AsyncAnalysisRunner(
    private val projectService: ProjectService,
    private val jobService: JobService,
    private val ingestionService: IngestionService,
) {

    @Async
    fun runRepoAnalysis(projectId: String, repoId: String, jobId: String, repoUrl: String, branch: String? = null, githubToken: String? = null) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 10, "리포지토리 클로닝 중...", project)

            ingestionService.ingestRepo(projectId, repoId, repoUrl, branch, githubToken) { step, message ->
                val analysisStep = when (step) {
                    "CLONING" -> AnalysisStep.CLONING
                    "SCANNING" -> AnalysisStep.SCANNING
                    "ANALYZING" -> AnalysisStep.ANALYZING
                    "PERSISTING" -> AnalysisStep.PERSISTING
                    else -> AnalysisStep.ANALYZING
                }
                val progress = when (step) {
                    "CLONING" -> 20
                    "SCANNING" -> 40
                    "ANALYZING" -> 60
                    "PERSISTING" -> 80
                    else -> 50
                }
                jobService.updateJobStep(jobId, analysisStep, progress, message)
            }

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "분석 완료")
        } catch (e: Exception) {
            logger.error(e) { "Analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
    }

    @Async
    fun runAllReposAnalysis(projectId: String, jobId: String, repos: List<Triple<String, String, String?>>, githubToken: String? = null) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 5, "멀티레포 분석 시작...", project)

            val totalRepos = repos.size
            repos.forEachIndexed { index, (repoId, gitUrl, branch) ->
                val repoName = gitUrl.substringAfterLast("/").removeSuffix(".git")
                val baseProgress = (90 * index / totalRepos)

                jobService.updateJobStep(jobId, AnalysisStep.CLONING, 5 + baseProgress, "[$repoName] 클로닝 중... (${index + 1}/$totalRepos)")

                ingestionService.ingestRepo(projectId, repoId, gitUrl, branch, githubToken) { step, message ->
                    val analysisStep = when (step) {
                        "CLONING" -> AnalysisStep.CLONING
                        "SCANNING" -> AnalysisStep.SCANNING
                        "ANALYZING" -> AnalysisStep.ANALYZING
                        "PERSISTING" -> AnalysisStep.PERSISTING
                        else -> AnalysisStep.ANALYZING
                    }
                    val stepOffset = when (step) {
                        "CLONING" -> 0
                        "SCANNING" -> 22
                        "ANALYZING" -> 45
                        "PERSISTING" -> 67
                        else -> 30
                    }
                    val progress = 5 + baseProgress + (90 / totalRepos * stepOffset / 100)
                    jobService.updateJobStep(jobId, analysisStep, progress.coerceAtMost(95), "[$repoName] $message (${index + 1}/$totalRepos)")
                }
            }

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "전체 분석 완료 (${totalRepos}개 레포)")
        } catch (e: Exception) {
            logger.error(e) { "Multi-repo analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
    }

    @Async
    fun runZipAnalysis(projectId: String, jobId: String, file: MultipartFile) {
        try {
            val project = projectService.updateStatus(projectId, ProjectStatus.INGESTING)
            jobService.updateJobStep(jobId, AnalysisStep.CLONING, 10, "ZIP 파일 추출 중...", project)

            ingestionService.ingestZip(projectId, file)

            jobService.updateJobStep(jobId, AnalysisStep.COMPLETED, 100, "분석 완료")
        } catch (e: Exception) {
            logger.error(e) { "ZIP analysis failed for job $jobId" }
            jobService.failJob(jobId, e.message ?: "Unknown error")
        }
    }
}
