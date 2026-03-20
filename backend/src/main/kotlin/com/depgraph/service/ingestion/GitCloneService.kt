package com.depgraph.service.ingestion

import com.depgraph.exception.IngestionException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
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

    fun clone(gitUrl: String, branch: String, githubToken: String? = null): Path {
        val targetDir = Path.of(workDirBase, generateDirName(gitUrl))
        Files.createDirectories(targetDir)

        log.info { "Cloning $gitUrl (branch: $branch) into $targetDir" }

        if (githubToken != null) {
            cloneWithJGit(gitUrl, branch, targetDir, githubToken)
        } else {
            // public 레포: JGit 시도 후 실패하면 git CLI fallback
            try {
                cloneWithJGit(gitUrl, branch, targetDir, null)
            } catch (ex: Exception) {
                log.warn { "JGit clone 실패, git CLI로 재시도: ${ex.message}" }
                // JGit이 만든 불완전한 디렉토리 정리
                targetDir.toFile().deleteRecursively()
                Files.createDirectories(targetDir)
                cloneWithGitCli(gitUrl, branch, targetDir)
            }
        }

        return targetDir
    }

    private fun cloneWithJGit(gitUrl: String, branch: String, targetDir: Path, githubToken: String?) {
        try {
            val cloneCommand = Git.cloneRepository()
                .setURI(gitUrl)
                .setBranch(branch)
                .setDirectory(targetDir.toFile())

            if (githubToken != null) {
                cloneCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider("token", githubToken)
                )
                cloneCommand.setDepth(1)
            }

            cloneCommand.call()
                .use { git ->
                    log.info { "JGit으로 클론 완료: ${git.repository.directory}" }
                }
        } catch (ex: GitAPIException) {
            throw IngestionException("Failed to clone repository: ${ex.message}", ex)
        }
    }

    private fun cloneWithGitCli(gitUrl: String, branch: String, targetDir: Path) {
        val command = listOf("git", "clone", "--depth", "1", "--branch", branch, gitUrl, targetDir.toString())
        log.info { "git CLI로 클론 실행: ${command.joinToString(" ")}" }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IngestionException("git CLI clone 실패 (exit=$exitCode): $output")
        }
        log.info { "git CLI로 클론 완료: $targetDir" }
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
