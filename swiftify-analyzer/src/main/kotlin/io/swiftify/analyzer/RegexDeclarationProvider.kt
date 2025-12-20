package io.swiftify.analyzer

import java.io.File

/**
 * Declaration provider that analyzes Kotlin source files using regex patterns.
 *
 * This provider is suitable for:
 * - Quick analysis without full compilation
 * - Preview/development workflows
 * - Simple projects without complex type inference needs
 *
 * Usage:
 * ```kotlin
 * val sourceFiles = listOf(File("src/MyClass.kt"), File("src/MyOther.kt"))
 * val provider = RegexDeclarationProvider(sourceFiles)
 * val declarations = provider.getDeclarations()
 * ```
 *
 * @param sourceFiles List of Kotlin source files to analyze
 */
class RegexDeclarationProvider(
    private val sourceFiles: List<File>,
) : DeclarationProvider {

    private val analyzer = KotlinDeclarationAnalyzer()

    override fun getDeclarations(): List<KotlinDeclaration> = sourceFiles
        .filter { it.exists() && it.extension == "kt" }
        .flatMap { file ->
            analyzer.analyze(file.readText())
        }

    override fun hasValidInput(): Boolean = sourceFiles.any { it.exists() && it.extension == "kt" }

    override fun getSourceDescription(): String {
        val count = sourceFiles.count { it.exists() && it.extension == "kt" }
        return "$count Kotlin source file(s)"
    }
}
