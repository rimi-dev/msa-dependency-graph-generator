package com.depgraph.controller

import com.depgraph.dto.ApiError
import com.depgraph.dto.ApiResponse
import com.depgraph.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UserResponse(
    val id: String,
    val login: String,
    val name: String?,
    val email: String?,
    val avatarUrl: String?
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val userRepository: UserRepository
) {
    @GetMapping("/me")
    fun me(): ResponseEntity<ApiResponse<UserResponse>> {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? String
            ?: return ResponseEntity.status(401)
                .body(ApiResponse.error(ApiError("UNAUTHORIZED", "Not authenticated")))

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.status(401)
                .body(ApiResponse.error(ApiError("UNAUTHORIZED", "User not found")))

        return ResponseEntity.ok(
            ApiResponse.success(
                UserResponse(
                    id = user.id,
                    login = user.login,
                    name = user.name,
                    email = user.email,
                    avatarUrl = user.avatarUrl
                )
            )
        )
    }
}
