package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AnalysisStep {
    CLONING, SCANNING, ANALYZING, PERSISTING, COMPLETED, FAILED
}

@Entity
@Table(name = "analysis_jobs")
class AnalysisJob(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var step: AnalysisStep = AnalysisStep.CLONING,

    @Column(nullable = false)
    var progress: Int = 0,

    @Column(nullable = false, length = 500)
    var message: String = "Job created",

    var error: String? = null,

    @Column(name = "repo_url", length = 1024)
    var repoUrl: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
