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

        // Remove existing services: per-repo or whole project
        if (repoId != null) {
            serviceRepository.deleteAllByRepoId(repoId)
        } else {
            serviceRepository.deleteAllByProjectId(projectId)
        }

        val detected = mutableListOf<Service>()

        // Check if root directory itself is a service (single-service repo)
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

        // Walk directories to find service roots, filtering excluded dirs
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
     * Recursively walk directories up to [maxDepth], skipping [excludedDirs].
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
            // Check if it has runnable scripts indicating a real service/app
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
