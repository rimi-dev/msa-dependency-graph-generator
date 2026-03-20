package com.depgraph.analyzer.util

import java.net.URI
import java.nio.file.Path

object ServiceNameResolver {

    // URL 내 환경변수 참조를 나타내는 패턴
    private val envVarInUrlPattern = Regex("""\$\{?([A-Z_]+)\}?""")

    // 서비스명으로 취급하지 않아야 하는 호스트
    private val IGNORED_HOSTS = setOf(
        "localhost", "127.0.0.1", "0.0.0.0", "::1",
    )

    /**
     * HTTP URL에서 서비스명을 추출합니다.
     * 예: "http://account-service:3000/api/users" -> "account-service"
     */
    fun resolveFromUrl(url: String, envVariables: Map<String, String> = emptyMap()): String? {
        // URL에 환경변수 참조가 있으면 확장
        val resolvedUrl = expandEnvVars(url, envVariables)

        return try {
            val uri = URI.create(resolvedUrl)
            val host = uri.host ?: return null
            if (host in IGNORED_HOSTS) return null
            // 포트가 있으면 제거하고 첫 번째 세그먼트 사용
            host.split(".").first().takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * gRPC 주소에서 서비스명을 추출합니다.
     * 예: "account-service:50051" -> "account-service"
     */
    fun resolveFromAddress(address: String, envVariables: Map<String, String> = emptyMap()): String? {
        val resolvedAddress = expandEnvVars(address, envVariables)

        // host:port 형식 처리
        val host = resolvedAddress.substringBefore(":").trim()
        if (host in IGNORED_HOSTS) return null
        return host.split(".").first().takeIf { it.isNotBlank() && !host.startsWith("$") }
    }

    /**
     * URL에서 경로 컴포넌트를 추출합니다.
     * 예: "http://account-service:3000/api/users" -> "/api/users"
     */
    fun extractPath(url: String): String? {
        return try {
            val uri = URI.create(url)
            uri.path?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * 프로젝트 루트 디렉토리명에서 서비스명을 추출합니다.
     */
    fun resolveServiceName(projectRoot: Path): String {
        return projectRoot.fileName?.toString() ?: "unknown"
    }

    private fun expandEnvVars(value: String, envVariables: Map<String, String>): String {
        if (envVariables.isEmpty()) return value

        var result = value
        envVarInUrlPattern.findAll(value).forEach { match ->
            val envKey = match.groupValues[1]
            val envValue = envVariables[envKey]
            if (envValue != null) {
                result = result.replace(match.value, envValue)
            }
        }
        return result
    }
}
