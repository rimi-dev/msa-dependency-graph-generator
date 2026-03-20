package com.depgraph.analyzer.plugins.nodejs

import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.plugin.AnalyzerPlugin
import com.depgraph.analyzer.util.ServiceNameResolver
import com.depgraph.analyzer.util.SourceLocationExtractor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class NestJsClientAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.nestjs.client"
    override val name = "NestJS Client Analyzer"
    override val supportedLanguages = listOf("typescript")
    override val supportedFrameworks = listOf("nestjs")

    // @Client({ name: 'service-name', ... }) 마이크로서비스 클라이언트 데코레이터 패턴
    private val nestClientPattern = Regex(
        """@Client\s*\(\s*\{[^}]*name\s*:\s*['"`]([^'"`]+)['"`]""",
    )

    // httpService.get("http://..."), httpService.post("http://...") 등
    // 일반 따옴표 및 템플릿 리터럴 지원
    private val nestHttpServicePattern = Regex(
        """httpService\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // httpService.get(`http://...`) 또는 httpService.get(`${baseUrl}/path`) — 표현식이 포함된 템플릿 리터럴
    private val nestHttpServiceTemplateLiteralPattern = Regex(
        """httpService\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*`([^`]+)`""",
    )

    // httpService.axiosRef.get("http://...") 또는 httpService.axiosRef.get(`...`)
    private val nestAxiosRefPattern = Regex(
        """httpService\s*\.\s*axiosRef\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`]([^'"`]+)['"`]""",
    )

    // this.xxxService.get/post("http://...") — 임의의 변수명으로 주입된 HttpService
    private val injectedHttpServicePattern = Regex(
        """this\s*\.\s*(\w+)\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )

    // HttpService 주입 감지: constructor(private xxx: HttpService)
    private val httpServiceInjectionPattern = Regex(
        """(?:private|protected|public)\s+(?:readonly\s+)?(\w+)\s*:\s*HttpService""",
    )

    // ClientProxy.send('pattern', data) or ClientProxy.emit('pattern', data)
    private val clientProxySendPattern = Regex(
        """(\w+)\s*\.\s*(send|emit)\s*\(\s*(?:\{[^}]*cmd\s*:\s*['"`]([^'"`]+)['"`][^}]*\}|['"`]([^'"`]+)['"`])""",
    )

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val dependencies = mutableListOf<DetectedDependency>()

        val targetFiles = context.files.filter { it.language in supportedLanguages }

        targetFiles.forEach { file ->
            // @Client 데코레이터 — 마이크로서비스 클라이언트 주입
            nestClientPattern.findAll(file.content).forEach { match ->
                val clientName = match.groupValues[1]

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = clientName,
                        protocol = "MQ",
                        method = null,
                        endpoint = null,
                        confidence = 0.85,
                        detectedBy = "$id.decorator",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // 생성자 주입에서 HttpService 변수명 감지
            val httpServiceVarNames = httpServiceInjectionPattern.findAll(file.content)
                .map { it.groupValues[1] }
                .toSet()

            // 리터럴 URL을 사용하는 HttpService 호출
            nestHttpServicePattern.findAll(file.content).forEach { match ->
                val method = match.groupValues[1].uppercase()
                val url = match.groupValues[2]
                val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = method,
                        endpoint = ServiceNameResolver.extractPath(url),
                        confidence = 0.9,
                        detectedBy = "$id.httpservice",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // 템플릿 리터럴을 사용하는 HttpService 호출 (${표현식} 포함 가능)
            nestHttpServiceTemplateLiteralPattern.findAll(file.content).forEach { match ->
                val method = match.groupValues[1].uppercase()
                val templateContent = match.groupValues[2]
                // 템플릿 내 환경변수를 확장한 후 서비스명 추출 시도
                val expandedUrl = resolveTemplateUrl(templateContent, context.envVariables)
                val target = ServiceNameResolver.resolveFromUrl(expandedUrl, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = method,
                        endpoint = ServiceNameResolver.extractPath(expandedUrl),
                        confidence = 0.8,
                        detectedBy = "$id.httpservice.template",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // httpService.axiosRef 호출
            nestAxiosRefPattern.findAll(file.content).forEach { match ->
                val method = match.groupValues[1].uppercase()
                val url = match.groupValues[2]
                val resolvedUrl = if (url.startsWith("http")) url else "http://$url"
                val target = ServiceNameResolver.resolveFromUrl(resolvedUrl, context.envVariables) ?: return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = target,
                        protocol = "HTTP",
                        method = method,
                        endpoint = ServiceNameResolver.extractPath(resolvedUrl),
                        confidence = 0.85,
                        detectedBy = "$id.axiosref",
                        sourceLocations = listOf(location),
                    ),
                )
            }

            // 커스텀 변수명으로 주입된 HttpService (this.xxxService.get(...))
            if (httpServiceVarNames.isNotEmpty()) {
                injectedHttpServicePattern.findAll(file.content).forEach { match ->
                    val varName = match.groupValues[1]
                    if (varName !in httpServiceVarNames && varName != "httpService") return@forEach

                    val method = match.groupValues[2].uppercase()
                    val url = match.groupValues[3]
                    val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables) ?: return@forEach

                    val location = SourceLocationExtractor.extract(file, match)
                    dependencies.add(
                        DetectedDependency(
                            source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                            target = target,
                            protocol = "HTTP",
                            method = method,
                            endpoint = ServiceNameResolver.extractPath(url),
                            confidence = 0.85,
                            detectedBy = "$id.injected",
                            sourceLocations = listOf(location),
                        ),
                    )
                }
            }

            // ClientProxy.send/emit 호출
            clientProxySendPattern.findAll(file.content).forEach { match ->
                val proxyVar = match.groupValues[1]
                val operation = match.groupValues[2]
                val pattern = match.groupValues[3].takeIf { it.isNotEmpty() }
                    ?: match.groupValues[4].takeIf { it.isNotEmpty() }
                    ?: return@forEach

                // 프록시가 아닌 것이 명확한 변수는 건너뜀
                if (proxyVar in setOf("this", "res", "req", "response", "request", "console")) return@forEach

                val location = SourceLocationExtractor.extract(file, match)
                dependencies.add(
                    DetectedDependency(
                        source = ServiceNameResolver.resolveServiceName(context.projectRoot),
                        target = proxyVar,
                        protocol = "MQ",
                        method = operation.uppercase(),
                        endpoint = pattern,
                        confidence = 0.75,
                        detectedBy = "$id.proxy",
                        sourceLocations = listOf(location),
                    ),
                )
            }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies" }
        return dependencies
    }

    /**
     * 템플릿 리터럴 URL에서 ${...} 표현식을 환경변수 값으로 치환하여 해석합니다.
     * 예: `${SERVICE_URL}/api/path` → `http://account-service:3000/api/path`
     */
    private fun resolveTemplateUrl(template: String, envVariables: Map<String, String>): String {
        val envRefPattern = Regex("""\$\{(?:process\.env\.)?([A-Z_a-z][A-Z_a-z0-9]*)\}""")
        var resolved = template
        envRefPattern.findAll(template).forEach { match ->
            val key = match.groupValues[1]
            val value = envVariables[key] ?: envVariables[key.uppercase()]
            if (value != null) {
                resolved = resolved.replace(match.value, value)
            }
        }
        // 아직 미해석 표현식이 남아있지만 호스트명 패턴을 포함하는 경우, 추출 시도
        if (!resolved.startsWith("http")) {
            resolved = "http://$resolved"
        }
        return resolved
    }
}
