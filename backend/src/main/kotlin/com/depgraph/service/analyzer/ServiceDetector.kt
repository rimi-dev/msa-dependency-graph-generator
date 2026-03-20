package com.depgraph.service.analyzer

import com.depgraph.domain.ProjectRepo
import com.depgraph.domain.Service
import com.depgraph.domain.TechStack
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.exception.ProjectRepoNotFoundException
import com.depgraph.repository.ProjectRepoRepository
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class ServiceDetector(
    private val projectRepository: ProjectRepository,
    private val projectRepoRepository: ProjectRepoRepository,
    private val serviceRepository: ServiceRepository,
) {

    private val excludedDirs = setOf(
        "node_modules", ".git", ".svn", ".hg",
        "dist", "build", "out", "target", ".next", ".nuxt",
        "test", "tests", "__tests__", "__test__", "spec",
        "coverage", ".nyc_output", ".jest",
        "docs", "doc", "documentation",
        "examples", "example", "samples", "sample",
        "scripts", "tools", "utils",
        ".gradle", ".idea", ".vscode", ".settings",
        "vendor", "venv", ".env", "env",
        ".cache", ".tmp", "tmp", "temp",
        "public", "static", "assets", "images", "img",
        "e2e", "cypress", "playwright",
        "fixtures", "__fixtures__", "__mocks__", "mocks",
        "storybook", ".storybook", "stories",
    )

    @Transactional
    fun detect(projectId: String, workDir: Path, repoId: String? = null): List<Service> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        val repo: ProjectRepo? = repoId?.let {
            projectRepoRepository.findById(it)
                .orElseThrow { ProjectRepoNotFoundException(it) }
        }

        // 기존 서비스 삭제: 레포 단위 또는 프로젝트 전체
        if (repoId != null) {
            serviceRepository.deleteAllByRepoId(repoId)
        } else {
            serviceRepository.deleteAllByProjectId(projectId)
        }

        val detected = mutableListOf<Service>()

        // 루트 디렉토리 자체가 서비스인지 확인 (단일 서비스 레포)
        detectTechStack(workDir)?.let { techStack ->
            val serviceName = workDir.fileName.toString()
            val service = Service(
                project = project,
                repo = repo,
                name = serviceName,
                path = ".",
                techStack = techStack,
            )
            detected.add(service)
            log.debug { "Detected root service: $serviceName ($techStack)" }
        }

        // 제외 디렉토리를 필터링하면서 서비스 루트를 탐색
        walkFiltered(workDir, maxDepth = 3) { dir ->
            detectTechStack(dir)?.let { techStack ->
                val relativePath = workDir.relativize(dir).toString()
                val serviceName = dir.fileName.toString()
                val service = Service(
                    project = project,
                    repo = repo,
                    name = serviceName,
                    path = relativePath,
                    techStack = techStack,
                )
                detected.add(service)
                log.debug { "Detected service: $serviceName ($techStack) at $relativePath" }
            }
        }

        return serviceRepository.saveAll(detected)
    }

    /**
     * [maxDepth]까지 디렉토리를 재귀적으로 탐색하며, [excludedDirs]는 건너뜁니다.
     */
    private fun walkFiltered(root: Path, maxDepth: Int, visitor: (Path) -> Unit) {
        doWalk(root, root, maxDepth, 0, visitor)
    }

    private fun doWalk(root: Path, current: Path, maxDepth: Int, depth: Int, visitor: (Path) -> Unit) {
        if (depth >= maxDepth) return
        val children = current.toFile().listFiles()?.filter { it.isDirectory } ?: return
        for (child in children) {
            val dirName = child.name
            if (dirName in excludedDirs) continue
            val childPath = child.toPath()
            visitor(childPath)
            doWalk(root, childPath, maxDepth, depth + 1, visitor)
        }
    }

    private fun detectTechStack(dir: Path): TechStack? {
        val files = dir.toFile().listFiles()?.map { it.name } ?: return null
        return when {
            "build.gradle.kts" in files || "build.gradle" in files -> TechStack.SPRING_BOOT
            "pom.xml" in files -> TechStack.SPRING_BOOT
            "package.json" in files && isActualNodeService(dir) -> detectNodeStack(dir)
            "requirements.txt" in files || "pyproject.toml" in files -> TechStack.FASTAPI
            "Gemfile" in files -> TechStack.RAILS
            else -> null
        }
    }

    private fun isActualNodeService(dir: Path): Boolean {
        val packageJsonFile = dir.resolve("package.json").toFile()
        if (!packageJsonFile.exists()) return false
        return try {
            val content = packageJsonFile.readText()
            // 실제 서비스/앱을 나타내는 실행 가능한 스크립트가 있는지 확인
            "\"start\"" in content || "\"dev\"" in content || "\"serve\"" in content || "\"build\"" in content
        } catch (ex: Exception) {
            false
        }
    }

    private fun detectNodeStack(dir: Path): TechStack {
        return try {
            val packageJson = dir.resolve("package.json").toFile().readText()
            when {
                "\"@nestjs/core\"" in packageJson -> TechStack.NODE_NEST
                else -> TechStack.NODE_EXPRESS
            }
        } catch (ex: Exception) {
            TechStack.NODE_EXPRESS
        }
    }
}
