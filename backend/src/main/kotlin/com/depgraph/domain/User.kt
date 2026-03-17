package com.depgraph.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Id
    val id: String = java.util.UUID.randomUUID().toString(),

    @Column(name = "github_id", nullable = false, unique = true)
    val githubId: Long,

    @Column(nullable = false, unique = true, length = 100)
    var login: String,

    @Column(length = 200)
    var name: String? = null,

    @Column(length = 300)
    var email: String? = null,

    @Column(name = "avatar_url", length = 1024)
    var avatarUrl: String? = null,

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
