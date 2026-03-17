package com.depgraph.analyzer.util

import java.net.URI
import java.nio.file.Path

object ServiceNameResolver {

    // Patterns that indicate environment variable references in URLs
    private val envVarInUrlPattern = Regex("""\$\{?([A-Z_]+)\}?""")

    /**
     * Resolves a service name from an HTTP URL.
     * e.g. "http://account-service:3000/api/users" -> "account-service"
     */
    fun resolveFromUrl(url: String, envVariables: Map<String, String> = emptyMap()): String? {
        // Expand env var references in URL if present
        val resolvedUrl = expandEnvVars(url, envVariables)

        return try {
            val uri = URI.create(resolvedUrl)
            val host = uri.host ?: return null
            // Strip port if present and use first segment
            host.split(".").first().takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * Resolves a service name from a gRPC address.
     * e.g. "account-service:50051" -> "account-service"
     */
    fun resolveFromAddress(address: String, envVariables: Map<String, String> = emptyMap()): String? {
        val resolvedAddress = expandEnvVars(address, envVariables)

        // Handle host:port format
        val host = resolvedAddress.substringBefore(":").trim()
        return host.split(".").first().takeIf { it.isNotBlank() && !host.startsWith("$") }
    }

    /**
     * Extracts the path component from a URL.
     * e.g. "http://account-service:3000/api/users" -> "/api/users"
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
     * Resolves the service name from the project root directory name.
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
