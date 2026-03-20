package com.depgraph.service.ingestion

import com.depgraph.analyzer.registry.AnalyzerRegistry
import com.depgraph.domain.Dependency
import com.depgraph.domain.DependencyType
import com.depgraph.domain.Service
import com.depgraph.domain.TechStack
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoNotFoundException
import com.depgraph.repository.DependencyRepository
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import com.depgraph.service.analyzer.DependencyAnalyzer
import com.depgraph.service.analyzer.ServiceDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@SpringService
class AnalysisOrchestrator(
    private val serviceDetector: ServiceDetector,
    private val dependencyAnalyzer: DependencyAnalyzer,
    private val analyzerRegistry: AnalyzerRegistry,
    private val projectRepository: ProjectRepository,
    private val projectRepoRepository: ProjectRepoRepository,
    private val serviceRepository: ServiceRepository,
    private val dependencyRepository: DependencyRepository,
) {

    @Transactional
    fun analyze(projectId: String, workDir: Path, repoId: String? = null) {
        log.info { "Starting analysis for project: $projectId, repoId: $repoId at $workDir" }

        val repo = repoId?.let {
            projectRepoRepository.findById(it)
                .orElseThrow { ProjectRepoNotFoundException(it) }
        }

        // 1단계: 레거시 서비스 탐지 (DB 저장, 레포 단위 인식)
        val detectedServices = serviceDetector.detect(projectId, workDir, repoId)
        log.info { "Detected ${detectedServices.size} services via legacy detector for project: $projectId" }

        // 2단계: 플러그인 레지스트리를 통한 향상된 서비스 탐지
        val pluginDetectedServices = analyzerRegistry.detectServices(workDir)
        log.info { "Plugin registry detected ${pluginDetectedServices.size} additional services for project: $projectId" }

        // 플러그인 탐지 서비스와 레거시 탐지 서비스 병합
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        val existingServiceNames = detectedServices.map { it.name.lowercase() }.toSet()
        val newServices = pluginDetectedServices
            .filter { it.name.lowercase() !in existingServiceNames }
            .map { detected ->
                Service(
                    project = project,
                    repo = repo,
                    name = detected.name,
                    path = detected.rootPath.removePrefix(workDir.toString()).trimStart('/'),
                    techStack = mapFrameworkToTechStack(detected.framework, detected.language),
                )
            }

        val savedNewServices: List<com.depgraph.domain.Service> = if (newServices.isNotEmpty()) {
            serviceRepository.saveAll(newServices).toList()
                .also { log.info { "Saved ${it.size} newly detected services for project: $projectId" } }
        } else emptyList()

        // 의존성 분석 시: 프로젝트의 모든 서비스 사용 (레포 간 교차 분석)
        var allProjectServices = serviceRepository.findAllByProjectId(projectId)

        // 프로젝트의 모든 의존성을 삭제하고 재분석 (레포 간 의존성)
        dependencyRepository.deleteAllBySourceProjectIdOrTargetProjectId(projectId, projectId)

        // 3단계: 플러그인 기반 향상된 의존성 분석 (누락된 서비스 자동 생성을 위해 먼저 실행)
        // 이름 변형(하이픈, 언더스코어, 구분자 없음)으로 서비스 조회 맵 구축
        val servicesByName = mutableMapOf<String, com.depgraph.domain.Service>()
        fun registerServiceVariants(svc: com.depgraph.domain.Service) {
            val name = svc.name.lowercase()
            servicesByName[name] = svc
            servicesByName[name.replace("-", "")] = svc
            servicesByName[name.replace("_", "")] = svc
            servicesByName[name.replace("-", "_")] = svc
            servicesByName[name.replace("_", "-")] = svc
        }
        allProjectServices.forEach { registerServiceVariants(it) }

        val allDependencies = mutableListOf<Dependency>()

        allProjectServices.forEach { sourceService ->
            val servicePath = workDir.resolve(sourceService.path ?: sourceService.name)
            val pluginDependencies = analyzerRegistry.analyzeDependencies(servicePath, sourceService.name)

            pluginDependencies.forEach { detected ->
                var targetService = servicesByName[detected.target.lowercase()]
                if (targetService == null) {
                    // HTTP URL을 통해 발견된 대상 서비스 자동 생성
                    log.info {
                        "Auto-creating service '${detected.target}' discovered via HTTP call " +
                            "(source=${sourceService.name}, detectedBy=${detected.detectedBy})"
                    }
                    val newService = serviceRepository.save(
                        Service(
                            project = project,
                            name = detected.target,
                            techStack = TechStack.UNKNOWN,
                        ),
                    )
                    registerServiceVariants(newService)
                    targetService = newService
                }
                if (targetService.id == sourceService.id) return@forEach

                allDependencies.add(
                    Dependency(
                        source = sourceService,
                        target = targetService,
                        type = mapProtocolToDependencyType(detected.protocol),
                        detail = buildDetail(detected),
                    ),
                )
            }
        }

        if (allDependencies.isNotEmpty()) {
            dependencyRepository.saveAll(allDependencies)
            log.info { "Saved ${allDependencies.size} plugin-detected dependencies for project: $projectId" }
        }

        // 4단계: 레거시 의존성 분석 (자동 생성된 서비스를 포함한 모든 서비스 사용)
        allProjectServices = serviceRepository.findAllByProjectId(projectId)
        dependencyAnalyzer.analyze(projectId, workDir, allProjectServices)
        log.info { "Legacy dependency analysis completed for project: $projectId" }

        log.info { "Full analysis completed for project: $projectId" }
    }

    private fun mapFrameworkToTechStack(framework: String?, language: String): TechStack {
        return when {
            framework == "nestjs" || framework == "nestjs-lib" -> TechStack.NODE_NEST
            framework == "express" || framework == "fastify" || framework == "koa" -> TechStack.NODE_EXPRESS
            language == "typescript" || language == "javascript" -> TechStack.NODE_EXPRESS
            language == "python" -> TechStack.FASTAPI
            language == "java" || language == "kotlin" -> TechStack.SPRING_BOOT
            else -> TechStack.UNKNOWN
        }
    }

    private fun mapProtocolToDependencyType(protocol: String): DependencyType {
        return when (protocol.uppercase()) {
            "HTTP", "HTTPS" -> DependencyType.HTTP
            "GRPC" -> DependencyType.GRPC
            "MQ", "AMQP", "KAFKA", "MESSAGE_QUEUE" -> DependencyType.MESSAGE_QUEUE
            "WEBSOCKET", "WS" -> DependencyType.WEBSOCKET
            else -> DependencyType.UNKNOWN
        }
    }

    private fun buildDetail(detected: com.depgraph.analyzer.model.DetectedDependency): String {
        return buildString {
            detected.method?.let { append("method=$it ") }
            detected.endpoint?.let { append("endpoint=$it ") }
            append("confidence=${detected.confidence} ")
            append("detectedBy=${detected.detectedBy}")
        }.trim()
    }
}
