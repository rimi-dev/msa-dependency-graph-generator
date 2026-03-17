package com.depgraph.analyzer.util

import com.depgraph.analyzer.model.SourceFile
import com.depgraph.analyzer.model.SourceLocation

object SourceLocationExtractor {

    /**
     * Extracts a SourceLocation from a MatchResult within a SourceFile.
     */
    fun extract(file: SourceFile, match: MatchResult): SourceLocation {
        val content = file.content
        val matchStart = match.range.first
        val matchEnd = match.range.last

        val startLine = content.substring(0, matchStart).count { it == '\n' } + 1
        val endLine = content.substring(0, matchEnd).count { it == '\n' } + 1

        val lines = content.lines()
        val snippetLines = lines.subList(
            (startLine - 1).coerceAtLeast(0),
            endLine.coerceAtMost(lines.size),
        )
        val snippet = snippetLines.joinToString("\n").trim()

        return SourceLocation(
            filePath = file.relativePath,
            startLine = startLine,
            endLine = endLine,
            snippet = snippet.take(500),
        )
    }

    /**
     * Scans a SourceFile to determine its programming language.
     */
    fun detectLanguage(file: SourceFile): String {
        val fileName = file.path.fileName.toString()
        return when {
            fileName.endsWith(".ts") || fileName.endsWith(".tsx") -> "typescript"
            fileName.endsWith(".js") || fileName.endsWith(".jsx") || fileName.endsWith(".mjs") -> "javascript"
            fileName.endsWith(".kt") -> "kotlin"
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".go") -> "go"
            fileName.endsWith(".rb") -> "ruby"
            fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> "yaml"
            fileName.endsWith(".json") -> "json"
            else -> "unknown"
        }
    }
}
