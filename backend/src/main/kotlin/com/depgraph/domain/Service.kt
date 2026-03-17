package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "services")
data class Service(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    val name: String,

    @Column(length = 2048)
    val path: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val techStack: TechStack = TechStack.UNKNOWN,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

enum class TechStack {
    SPRING_BOOT,
    NODE_EXPRESS,
    NODE_NEST,
    FASTAPI,
    DJANGO,
    RAILS,
    UNKNOWN,
}
