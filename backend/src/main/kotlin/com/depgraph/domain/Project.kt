package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "projects")
data class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val slug: String,

    @Column(length = 2048)
    val description: String? = null,

    @Column(name = "git_url", length = 1024)
    val gitUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ProjectStatus = ProjectStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

enum class ProjectStatus {
    PENDING,
    INGESTING,
    ANALYZING,
    READY,
    ERROR,
}
