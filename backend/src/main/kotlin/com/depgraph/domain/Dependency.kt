package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "dependencies")
data class Dependency(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id", nullable = false)
    val source: Service,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_service_id", nullable = false)
    val target: Service,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: DependencyType = DependencyType.HTTP,

    @Column(length = 1024)
    val detail: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

enum class DependencyType {
    HTTP,
    GRPC,
    MESSAGE_QUEUE,
    DATABASE_SHARED,
    WEBSOCKET,
    UNKNOWN,
}
