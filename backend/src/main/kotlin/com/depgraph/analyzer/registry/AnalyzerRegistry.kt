package com.depgraph.analyzer.registry

import com.depgraph.analyzer.config.ConfigFileParser
import com.depgraph.analyzer.model.AnalysisContext
import com.depgraph.analyzer.model.DetectedDependency
import com.depgraph.analyzer.model.DetectedService
import com.depgraph.analyzer.model.SourceFile
import com.depgraph.analyzer.plugin.AnalyzerPlugin
import com.depgraph.analyzer.plugin.ServiceDetectorPlugin
import com.depgraph.analyzer.util.SourceLocationExtractor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Component
class AnalyzerRegistry(
    private val analyzerPlugins: List<AnalyzerPlugin>,
    private val serviceDetectorPlugins: List<ServiceDetectorPlugin>,
    private val configFileParser: ConfigFileParser,
) {

    companion object {
        private val SOURCE_EXTENSIONS = setOf(
            ".ts", ".tsx", ".js", ".jsx", ".mjs",
            ".kt", ".java", ".py", ".go", ".rb",
        )

        private val IGNORED_DIRECTORIES = setOf(
            "node_modules", ".git", "dist", "build", "out",
            ".next", ".nuxt", "coverage", "__pycache__", ".gradle", "target",
        )

        private val TEST_FILE_PATTERNS = setOf(
            ".test.", ".spec.", ".mock.", ".stub.", ".e2e-spec.", ".e2e.",
            "__test__", "__tests__", "__mock__", "__mocks__",
        )

        private const val MAX_FILE_SIZE_BYTES = 1_000_000L // 1 MB
    }

    /**
     * Detects all services in the given project root using all registered ServiceDetectorPlugins.
     */
    fun detectServices(projectRoot: Path): List<DetectedService> {
        log.info { "Running ${serviceDetectorPlugins.size} service detector plugins on $projectRoot" }

        return serviceDetectorPlugins
            .flatMap { plugin ->
                runCatching { plugin.detect(projectRoot) }
                    .onFailure { log.warn { "ServiceDetectorPlugin [${plugin.id}] failed: ${it.message}" } }
                    .getOrElse { emptyList() }
            }
            .distinctBy { it.name }
            .also { log.info { "Total detected services: ${it.size}" } }
    }

    /**
     * Runs all registered AnalyzerPlugins to detect dependencies in the given context.
     */
    fun analyzeDependencies(projectRoot: Path, serviceName: String): List<DetectedDependency> {
        val context = buildAnalysisContext(projectRoot)

        log.info { "Running ${analyzerPlugins.size} analyzer plugins for service '$serviceName'" }

        return analyzerPlugins
            .flatMap { plugin ->
                runCatching { plugin.analyze(context) }
                    .onFailure { log.warn { "AnalyzerPlugin [${plugin.id}] failed: ${it.message}" } }
                    .getOrElse { emptyList() }
            }
            .also { log.info { "Total detected dependencies: ${it.size}" } }
    }

    /**
     * Builds an AnalysisContext for the given project root by scanning source files,
     * parsing config files, and collecting env variables.
     */
    fun buildAnalysisContext(projectRoot: Path): AnalysisContext {
        val sourceFiles = scanSourceFiles(projectRoot)
        val (configFiles, envVariables) = loadConfigFiles(projectRoot)

        log.debug {
            "Built analysis context: ${sourceFiles.size} source files, " +
                "${configFiles.size} config files, ${envVariables.size} env vars"
        }

        return AnalysisContext(
            projectRoot = projectRoot,
            files = sourceFiles,
            configFiles = configFiles,
            envVariables = envVariables,
        )
    }

    private fun scanSourceFiles(root: Path): List<SourceFile> {
        if (!Files.exists(root)) return emptyList()

        val sourceFiles = mutableListOf<SourceFile>()

        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path -> isSourceFile(path) }
                .filter { path -> !isInIgnoredDirectory(path, root) }
                .filter { path -> Files.size(path) <= MAX_FILE_SIZE_BYTES }
                .forEach { path ->
                    runCatching {
                        val content = Files.readString(path)
                        val relativePath = root.relativize(path).toString()
                        val tempFile = SourceFile(
                            path = path,
                            relativePath = relativePath,
                            content = content,
                            language = "unknown",
                        )
                        sourceFiles.add(
                            tempFile.copy(language = SourceLocationExtractor.detectLanguage(tempFile)),
                        )
                    }.onFailure {
                        log.debug { "Could not read source file $path: ${it.message}" }
                    }
                }
        }

        return sourceFiles
    }

    private fun loadConfigFiles(root: Path): Pair<Map<String, Any>, Map<String, String>> {
        val configFiles = mutableMapOf<String, Any>()
        val envVariables = mutableMapOf<String, String>()

        // Load docker-compose
        listOf("docker-compose.yml", "docker-compose.yaml").forEach { name ->
            val path = root.resolve(name)
            if (Files.exists(path)) {
                val parsed = configFileParser.parseDockerCompose(path)
                configFiles[name] = parsed
                // Extract env vars from docker-compose services
                envVariables.putAll(configFileParser.extractDockerComposeEnvVars(parsed))
            }
        }

        // Load package.json
        val packageJsonPath = root.resolve("package.json")
        if (Files.exists(packageJsonPath)) {
            configFiles["package.json"] = configFileParser.parsePackageJson(packageJsonPath)
        }

        // Load nest-cli.json
        val nestCliPath = root.resolve("nest-cli.json")
        if (Files.exists(nestCliPath)) {
            configFiles["nest-cli.json"] = configFileParser.parseNestCliJson(nestCliPath)
        }

        // Load .env files
        listOf(".env", ".env.local", ".env.development", ".env.production").forEach { name ->
            val path = root.resolve(name)
            if (Files.exists(path)) {
                val parsed = configFileParser.parseEnvFile(path)
                envVariables.putAll(parsed)
            }
        }

        return configFiles to envVariables
    }

    private fun isSourceFile(path: Path): Boolean {
        val name = path.fileName.toString()
        if (TEST_FILE_PATTERNS.any { name.contains(it) }) return false
        return SOURCE_EXTENSIONS.any { name.endsWith(it) }
    }

    private fun isInIgnoredDirectory(path: Path, root: Path): Boolean {
        val relative = root.relativize(path)
        return (0 until relative.nameCount).any { i ->
            relative.getName(i).toString() in IGNORED_DIRECTORIES
        }
    }
}
