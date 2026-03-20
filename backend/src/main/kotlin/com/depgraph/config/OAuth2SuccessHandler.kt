package com.depgraph.config

import com.depgraph.domain.User
import com.depgraph.repository.UserRepository
import com.depgraph.service.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val authorizedClientService: OAuth2AuthorizedClientService,
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

        // 인가된 클라이언트에서 GitHub 액세스 토큰 추출
        val githubAccessToken = extractGithubAccessToken(authentication)

        val user = userRepository.findByGithubId(githubId).orElse(null)?.apply {
            this.login = login
            this.name = name
            this.email = email
            this.avatarUrl = avatarUrl
            this.githubAccessToken = githubAccessToken
        } ?: User(
            githubId = githubId,
            login = login,
            name = name,
            email = email,
            avatarUrl = avatarUrl,
            githubAccessToken = githubAccessToken
        )
        userRepository.save(user)

        val token = jwtService.generateToken(user.id, user.login)
        response.sendRedirect("$frontendUrl/oauth/callback?token=$token")
    }

    private fun extractGithubAccessToken(authentication: Authentication): String? {
        if (authentication !is OAuth2AuthenticationToken) return null
        val client = authorizedClientService.loadAuthorizedClient<org.springframework.security.oauth2.client.OAuth2AuthorizedClient>(
            authentication.authorizedClientRegistrationId,
            authentication.name
        ) ?: return null
        return client.accessToken.tokenValue
    }
}
