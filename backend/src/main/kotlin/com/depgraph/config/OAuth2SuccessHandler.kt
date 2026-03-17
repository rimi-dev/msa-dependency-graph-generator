package com.depgraph.config

import com.depgraph.domain.User
import com.depgraph.repository.UserRepository
import com.depgraph.service.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    @Value("\${app.frontend-url:http://localhost:5173}") private val frontendUrl: String
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User
        val attributes = oauth2User.attributes

        val githubId = (attributes["id"] as Number).toLong()
        val login = attributes["login"] as String
        val name = attributes["name"] as? String
        val email = attributes["email"] as? String
        val avatarUrl = attributes["avatar_url"] as? String

        val user = userRepository.findByGithubId(githubId).orElse(null)?.apply {
            this.login = login
            this.name = name
            this.email = email
            this.avatarUrl = avatarUrl
        } ?: User(
            githubId = githubId,
            login = login,
            name = name,
            email = email,
            avatarUrl = avatarUrl
        )
        userRepository.save(user)

        val token = jwtService.generateToken(user.id, user.login)
        response.sendRedirect("$frontendUrl/oauth/callback?token=$token")
    }
}
