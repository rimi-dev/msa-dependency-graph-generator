package com.depgraph.repository

import com.depgraph.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, String> {
    fun findByGithubId(githubId: Long): Optional<User>
    fun findByLogin(login: String): Optional<User>
}
