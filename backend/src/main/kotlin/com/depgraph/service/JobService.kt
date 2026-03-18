package com.depgraph.service

import com.depgraph.domain.AnalysisJob
import com.depgraph.domain.AnalysisStep
import com.depgraph.domain.Project
import com.depgraph.domain.ProjectRepo
import com.depgraph.dto.JobStatusResponse
import com.depgraph.exception.JobNotFoundException
import com.depgraph.repository.AnalysisJobRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class JobService(
    private val analysisJobRepository: AnalysisJobRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {
    fun createJob(repoUrl: String? = null, repo: ProjectRepo? = null): AnalysisJob {
        val job = AnalysisJob(repoUrl = repoUrl, repo = repo)
        return analysisJobRepository.save(job).also {
            logger.info { "Created analysis job: ${it.id}" }
        }
    }

    fun getJob(jobId: String): AnalysisJob {
        return analysisJobRepository.findById(jobId)
            .orElseThrow { JobNotFoundException(jobId) }
    }

    fun getJobStatus(jobId: String): JobStatusResponse {
        val job = getJob(jobId)
        return job.toResponse()
    }

    fun updateJobStep(jobId: String, step: AnalysisStep, progress: Int, message: String, project: Project? = null) {
        val job = getJob(jobId)
        job.step = step
        job.progress = progress
        job.message = message
        if (project != null) job.project = project
        analysisJobRepository.save(job)
        notifyProgress(job)
    }

    fun failJob(jobId: String, error: String) {
        val job = getJob(jobId)
        job.step = AnalysisStep.FAILED
        job.progress = job.progress
        job.message = "Analysis failed"
        job.error = error
        analysisJobRepository.save(job)
        notifyProgress(job)
    }

    private fun notifyProgress(job: AnalysisJob) {
        val response = job.toResponse()
        messagingTemplate.convertAndSend("/topic/jobs/${job.id}/progress", response)
        logger.debug { "Sent job progress: ${job.id} → ${job.step} (${job.progress}%)" }
    }

    private fun AnalysisJob.toResponse() = JobStatusResponse(
        jobId = id,
        projectId = project?.id,
        step = step,
        progress = progress,
        message = message,
        createdAt = createdAt,
        updatedAt = updatedAt,
        error = error
    )
}
