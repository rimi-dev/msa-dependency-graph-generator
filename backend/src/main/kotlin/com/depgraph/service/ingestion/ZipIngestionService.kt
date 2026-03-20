package com.depgraph.service.ingestion

import com.depgraph.exception.IngestionException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.compress.archivers.zip.ZipFile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class ZipIngestionService(
    @Value("\${depgraph.ingestion.work-dir:/tmp/depgraph/repos}")
    private val workDirBase: String,
) {

    fun extract(zipFile: MultipartFile): Path {
        val targetDir = Path.of(workDirBase, "upload_${System.currentTimeMillis()}")
        Files.createDirectories(targetDir)

        val tempZip = Files.createTempFile("upload_", ".zip")
        try {
            zipFile.transferTo(tempZip)
            log.info { "Extracting ZIP to $targetDir" }
            extractZip(tempZip, targetDir)
        } catch (ex: Exception) {
            throw IngestionException("Failed to extract ZIP: ${ex.message}", ex)
        } finally {
            Files.deleteIfExists(tempZip)
        }

        return targetDir
    }

    fun extractFromPath(zipPath: Path): Path {
        val targetDir = Path.of(workDirBase, "upload_${System.currentTimeMillis()}")
        Files.createDirectories(targetDir)

        try {
            log.info { "Extracting ZIP from path to $targetDir" }
            extractZip(zipPath, targetDir)
        } catch (ex: Exception) {
            throw IngestionException("Failed to extract ZIP: ${ex.message}", ex)
        }

        return targetDir
    }

    private fun extractZip(zipPath: Path, targetDir: Path) {
        ZipFile.builder()
            .setFile(zipPath.toFile())
            .get()
            .use { zip ->
                zip.entries.asSequence().forEach { entry ->
                    val entryPath = targetDir.resolve(entry.name).normalize()
                    if (!entryPath.startsWith(targetDir)) {
                        throw IngestionException("ZIP entry outside target directory: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(entryPath)
                    } else {
                        Files.createDirectories(entryPath.parent)
                        zip.getInputStream(entry).use { input ->
                            Files.copy(input, entryPath)
                        }
                    }
                }
            }
    }
}
