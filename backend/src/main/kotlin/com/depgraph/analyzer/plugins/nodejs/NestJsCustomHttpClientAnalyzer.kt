package com.depgraph.analyzer.plugins.nodejs

import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.plugin.AnalyzerPlugin
import com.depgraph.analyzer.util.ServiceNameResolver
import com.depgraph.analyzer.util.SourceLocationExtractor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * NestJS 커스텀 HTTP 래퍼 서비스를 통한 의존성을 감지하는 분석기.
 *
 * 2-Pass 분석:
 * - Pass 1: @Injectable() 클래스 중 HttpService/axios를 사용하는 래퍼 서비스 식별
 * - Pass 2: 래퍼 서비스를 DI로 주입받아 HTTP 호출하는 코드 감지
 */
@Component
class NestJsCustomHttpClientAnalyzer : AnalyzerPlugin {

    override val id = "nodejs.nestjs.custom-http-client"
    override val name = "NestJS Custom HTTP Client Analyzer"
    override val supportedLanguages = listOf("typescript")
    override val supportedFrameworks = listOf("nestjs")

    // Pass 1 — 래퍼 서비스 식별 패턴
    private val injectableClassPattern = Regex(
        """@Injectable\s*\(\s*\)\s*(?:export\s+)?class\s+(\w+)""",
    )
    private val httpClientIndicators = listOf(
        Regex("""\bHttpService\b"""),
        Regex("""\baxios\b"""),
        Regex("""\bAxiosInstance\b"""),
        Regex("""\baxiosRef\b"""),
    )

    // Pass 2 — 래퍼 사용처 감지 패턴
    private val constructorParamPattern = Regex(
        """(?:private|protected|public)\s+(?:readonly\s+)?(\w+)\s*:\s*(\w+)""",
    )
    private val wrapperCallPattern = Regex(
        """this\s*\.\s*(\w+)\s*\.\s*(get|post|put|delete|patch|request)\s*(?:<[^>]*>)?\s*\(\s*['"`](https?://[^'"`]+)['"`]""",
    )
    private val wrapperCallTemplateLiteralPattern = Regex(
        """this\s*\.\s*(\w+)\s*\.\s*(get|post|put|delete|patch|request)\s*(?:<[^>]*>)?\s*\(\s*`(https?://[^`]+)`""",
    )

    // 기존 NestJsClientAnalyzer가 처리하는 변수명 — 중복 방지
    private val skipVariableNames = setOf("httpService")

    override fun analyze(context: AnalysisContext): List<DetectedDependency> {
        val targetFiles = context.files.filter { it.language in supportedLanguages }

        // Pass 1: 래퍼 서비스 클래스명 수집
        val wrapperServiceNames = mutableSetOf<String>()
        targetFiles.forEach { file ->
            val classNames = injectableClassPattern.findAll(file.content)
                .map { it.groupValues[1] }
                .toList()
            if (classNames.isNotEmpty()) {
                val hasHttpClient = httpClientIndicators.any { it.containsMatchIn(file.content) }
                if (hasHttpClient) {
                    wrapperServiceNames.addAll(classNames)
                }
            }
        }

        if (wrapperServiceNames.isEmpty()) {
            log.debug { "[$id] No custom HTTP wrapper services found" }
            return emptyList()
        }

        log.debug { "[$id] Found wrapper services: $wrapperServiceNames" }

        // Pass 2: 래퍼 서비스 사용처에서 HTTP 호출 감지
        val dependencies = mutableListOf<DetectedDependency>()
        val sourceName = ServiceNameResolver.resolveServiceName(context.projectRoot)

        targetFiles.forEach { file ->
            // constructor 파라미터에서 래퍼 서비스 타입의 변수명 추출
            val wrapperVarNames = constructorParamPattern.findAll(file.content)
                .filter { it.groupValues[2] in wrapperServiceNames }
                .map { it.groupValues[1] }
                .filter { it !in skipVariableNames }
                .toSet()

            if (wrapperVarNames.isEmpty()) return@forEach

            // 래퍼 변수를 통한 HTTP 호출 감지
            val callPatterns = listOf(wrapperCallPattern, wrapperCallTemplateLiteralPattern)
            callPatterns.forEach { pattern ->
                pattern.findAll(file.content).forEach { match ->
                    val varName = match.groupValues[1]
                    if (varName !in wrapperVarNames) return@forEach

                    val method = match.groupValues[2].uppercase()
                    val url = match.groupValues[3]
                    val target = ServiceNameResolver.resolveFromUrl(url, context.envVariables)
                        ?: return@forEach

                    val location = SourceLocationExtractor.extract(file, match)
                    dependencies.add(
                        DetectedDependency(
                            source = sourceName,
                            target = target,
                            protocol = "HTTP",
                            method = method,
                            endpoint = ServiceNameResolver.extractPath(url),
                            confidence = 0.85,
                            detectedBy = "$id.wrapper",
                            sourceLocations = listOf(location),
                        ),
                    )
                }
            }
        }

        log.debug { "[$id] Found ${dependencies.size} dependencies via custom HTTP wrappers" }
        return dependencies
    }
}
