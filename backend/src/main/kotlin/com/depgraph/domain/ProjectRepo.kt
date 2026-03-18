package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "project_repos",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "git_url"])]
)
data class ProjectRepo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(name = "git_url", nullable = false, length = 1024)
    val gitUrl: String,

    @Column(length = 255)
    val branch: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ProjectRepoStatus = ProjectRepoStatus.PENDING,

    @Column(name = "last_analyzed_at")
    val lastAnalyzedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

enum class ProjectRepoStatus {
    PENDING,
    INGESTING,
    ANALYZING,
    READY,
    ERROR,
}
