package com.depgraph.service.analyzer

import com.depgraph.domain.Service
import com.depgraph.domain.TechStack
import com.depgraph.repository.ProjectRepository
import com.depgraph.repository.ServiceRepository
import com.depgraph.exception.ProjectNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

private val log = KotlinLogging.logger {}

@Component
class ServiceDetector(
    private val projectRepository: ProjectRepository,
    private val serviceRepository: ServiceRepository,
) {

    fun detect(projectId: String, workDir: Path): List<Service> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException(projectId) }

        // Remove existing services for re-analysis
        serviceRepository.deleteAllByProjectId(projectId)

        val detected = mutableListOf<Service>()

        // Walk directories to find service roots
        Files.walk(workDir, 3).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { it != workDir }
                .forEach { dir ->
                    detectTechStack(dir)?.let { techStack ->
                        val relativePath = workDir.relativize(dir).toString()
                        val serviceName = dir.fileName.toString()
                        val service = Service(
                            project = project,
                            name = serviceName,
                            path = relativePath,
                            techStack = techStack,
                        )
                        detected.add(service)
                        log.debug { "Detected service: $serviceName ($techStack) at $relativePath" }
                    }
                }
        }

        return serviceRepository.saveAll(detected)
    }

    private fun detectTechStack(dir: Path): TechStack? {
        val files = dir.toFile().listFiles()?.map { it.name } ?: return null
        return when {
            "build.gradle.kts" in files || "build.gradle" in files -> TechStack.SPRING_BOOT
            "pom.xml" in files -> TechStack.SPRING_BOOT
            "package.json" in files -> detectNodeStack(dir)
            "requirements.txt" in files || "pyproject.toml" in files -> TechStack.FASTAPI
            "Gemfile" in files -> TechStack.RAILS
            else -> null
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
