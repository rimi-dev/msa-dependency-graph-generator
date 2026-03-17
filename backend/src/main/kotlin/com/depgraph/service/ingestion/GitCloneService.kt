package com.depgraph.service.ingestion

import com.depgraph.exception.IngestionException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class GitCloneService(
    @Value("\${depgraph.ingestion.work-dir:/tmp/depgraph/repos}")
    private val workDirBase: String,
) {

    fun clone(gitUrl: String, branch: String): Path {
        val targetDir = Path.of(workDirBase, generateDirName(gitUrl))
        Files.createDirectories(targetDir)

        log.info { "Cloning $gitUrl (branch: $branch) into $targetDir" }

        try {
            Git.cloneRepository()
                .setURI(gitUrl)
                .setBranch(branch)
                .setDirectory(targetDir.toFile())
                .setDepth(1)
                .call()
                .use { git ->
                    log.info { "Successfully cloned: ${git.repository.directory}" }
                }
        } catch (ex: GitAPIException) {
            throw IngestionException("Failed to clone repository: ${ex.message}", ex)
        }

        return targetDir
    }

    private fun generateDirName(gitUrl: String): String {
        return gitUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("git@")
            .replace(":", "/")
            .replace("/", "_")
            .replace(".git", "")
            .take(100) + "_" + System.currentTimeMillis()
    }
}
