package com.depgraph.service.analyzer

import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import com.depgraph.domain.Service
import com.depgraph.repository.DependencyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class DependencyAnalyzer(
    private val dependencyRepository: DependencyRepository,
) {

    // HTTP 호출 패턴 — URL 또는 HTTP 클라이언트 호출에서 서비스명이 포함된 경우만 감지
    private val httpCallPatterns = listOf(
        // URL 리터럴: http(s)://서비스명.xxx 또는 http(s)://xxx/서비스명 (quotes + template literals)
        Regex("""https?://[^"'\s`]*"""),
        // Spring RestTemplate / WebClient
        Regex("""restTemplate\s*\.\s*(get|post|put|delete|patch|exchange)\w*\s*\(\s*["'][^"']*"""),
        Regex("""webClient\s*\.\s*(get|post|put|delete|patch)\s*\(\s*\)\s*\.uri\s*\(\s*["'][^"']*"""),
        // Axios (quotes + template literals)
        Regex("""axios\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"`][^'"`]*"""),
        Regex("""axios\s*\(\s*\{[^}]*url\s*:\s*['"`][^'"`]*"""),
        // Fetch API
        Regex("""fetch\s*\(\s*['"`][^'"`]*"""),
        // NestJS HttpService (this.httpService.get/post/...) — quotes + template literals
        Regex("""httpService\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`][^'"`]*"""),
        // NestJS HttpService via axiosRef
        Regex("""httpService\s*\.\s*axiosRef\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`][^'"`]*"""),
        // NestJS injected HTTP client (this.xxxClient.get/post/...) with URL
        Regex("""this\s*\.\s*\w+(?:Client|Service|Http|Api)\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`][^'"`]*"""),
        // Spring @FeignClient
        Regex("""@FeignClient\s*\([^)]*(?:name|value)\s*=\s*["'][^"']*"""),
        // 환경변수/설정에서 서비스 URL 참조
        Regex("""[A-Z_]*(?:URL|HOST|ENDPOINT|BASE_URL|SERVICE)\s*[:=]\s*["']?https?://[^"'\s]*"""),
        Regex("""[a-z._-]*(?:url|host|endpoint|base-url|service)[a-z._-]*\s*[:=]\s*["']?https?://[^"'\s]*"""),
    )

    private val excludedDirs = setOf(
        "node_modules", ".git", "dist", "build", "out", "target",
        ".next", ".nuxt", "coverage", ".gradle",
        "vendor", "venv", ".env",
    )

    private val genericNames = setOf(
        "app", "api", "web", "server", "client", "test", "tests",
        "lib", "core", "common", "shared", "utils", "config",
        "src", "main", "index", "root", "base",
    )

    fun analyze(projectId: String, workDir: Path, services: List<Service>): List<Dependency> {
        val serviceNames = services.associateBy { it.name.lowercase() }
        val dependencies = mutableListOf<Dependency>()

        services.forEach { source ->
            val servicePath = workDir.resolve(source.path ?: source.name)
            if (!Files.exists(servicePath)) return@forEach

            val foundTargets = scanForHttpDependencies(servicePath, serviceNames, source.name)
            foundTargets.forEach { targetService ->
                if (targetService.id != source.id) {
                    dependencies.add(
                        Dependency(
                            source = source,
                            target = targetService,
                            type = DependencyType.HTTP,
                        )
                    )
                }
            }
        }

        val saved = dependencyRepository.saveAll(dependencies)
        log.info { "Saved ${saved.size} HTTP dependencies for project: $projectId" }
        return saved
    }

    private fun scanForHttpDependencies(
        servicePath: Path,
        serviceNames: Map<String, Service>,
        currentServiceName: String,
    ): List<Service> {
        val results = mutableSetOf<String>() // serviceId set for dedup

        Files.walk(servicePath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    excludedDirs.none { excluded ->
                        path.toString().contains("/$excluded/") || path.toString().contains("\\$excluded\\")
                    }
                }
                .filter { isSourceFile(it) }
                .forEach { file ->
                    try {
                        val content = Files.readString(file)

                        // HTTP 호출 패턴에서 서비스명을 찾음
                        val httpContexts = httpCallPatterns.flatMap { pattern ->
                            pattern.findAll(content).map { it.value }
                        }

                        if (httpContexts.isEmpty()) return@forEach

                        val httpContextJoined = httpContexts.joinToString(" ").lowercase()

                        serviceNames.forEach { (name, service) ->
                            if (name == currentServiceName.lowercase()) return@forEach
                            if (name in genericNames) return@forEach

                            // 서비스명이 HTTP 호출 컨텍스트(URL, 클라이언트 호출) 안에 포함되어야 함
                            val namePattern = if (name.length <= 3) {
                                Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE)
                            } else {
                                Regex("""(?<![a-zA-Z])${Regex.escape(name)}(?![a-zA-Z])""", RegexOption.IGNORE_CASE)
                            }

                            if (namePattern.containsMatchIn(httpContextJoined) && service.id != null) {
                                results.add(service.id!!)
                            }
                        }
                    } catch (ex: Exception) {
                        log.debug { "Could not read file: $file - ${ex.message}" }
                    }
                }
        }

        return serviceNames.values.filter { it.id in results }
    }

    private fun isSourceFile(path: Path): Boolean {
        val name = path.fileName.toString()
        return name.endsWith(".kt") ||
            name.endsWith(".java") ||
            name.endsWith(".ts") ||
            name.endsWith(".js") ||
            name.endsWith(".py") ||
            name.endsWith(".yml") ||
            name.endsWith(".yaml") ||
            name.endsWith(".properties")
    }
}
