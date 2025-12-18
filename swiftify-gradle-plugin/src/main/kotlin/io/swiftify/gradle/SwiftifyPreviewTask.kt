package io.swiftify.gradle

import io.swiftify.generator.SwiftifyTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to preview generated Swift code for Kotlin declarations.
 *
 * Usage:
 * ```bash
 * ./gradlew swiftifyPreview
 * ./gradlew swiftifyPreview --class=com.example.NetworkResult
 * ```
 */
abstract class SwiftifyPreviewTask : DefaultTask() {

    /**
     * Specific class to preview. If not set, previews all transformable declarations.
     */
    @get:Input
    @get:Optional
    abstract val targetClass: Property<String>

    /**
     * Kotlin source files to analyze.
     */
    @get:InputFiles
    @get:Optional
    abstract val kotlinSources: ConfigurableFileCollection

    private val transformer = SwiftifyTransformer()

    init {
        group = "swiftify"
        description = "Preview generated Swift code for Kotlin declarations"
    }

    @TaskAction
    fun preview() {
        logger.lifecycle(buildPreviewHeader())

        val sourceFiles = findKotlinSources()
        if (sourceFiles.isEmpty()) {
            logger.lifecycle("No Kotlin source files found.")
            logger.lifecycle("Configure sources with: swiftifyPreview { kotlinSources.from(...) }")
            return
        }

        val target = if (targetClass.isPresent) targetClass.get() else null

        sourceFiles.forEach { file ->
            val source = file.readText()

            // If a target is specified, only process files containing that class
            if (target != null && !source.contains(target.substringAfterLast("."))) {
                return@forEach
            }

            try {
                val result = transformer.transform(source)

                if (result.declarationsTransformed > 0) {
                    logger.lifecycle(buildPreview(file.name, source, result.swiftCode))
                }
            } catch (e: Exception) {
                logger.warn("Swiftify: Warning - could not transform ${file.name}: ${e.message}")
                logger.debug("Swiftify: Full error", e)
            }
        }

        if (target != null) {
            logger.lifecycle("\nFiltered by class: $target")
        }
    }

    private fun findKotlinSources(): List<File> {
        // Try configured sources first
        if (!kotlinSources.isEmpty) {
            return kotlinSources.files.filter { it.extension == "kt" }
        }

        // Fall back to finding sources in the project
        val srcDirs = listOf(
            project.file("src/commonMain/kotlin"),
            project.file("src/main/kotlin"),
            project.file("src/iosMain/kotlin")
        )

        return srcDirs.filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
    }

    private fun buildPreviewHeader(): String = """
        |
        |╔════════════════════════════════════════════════════════════════╗
        |║                    Swiftify Preview                            ║
        |╚════════════════════════════════════════════════════════════════╝
        |
    """.trimMargin()

    private fun buildPreview(fileName: String, kotlinSource: String, swiftCode: String): String {
        val kotlinPreview = kotlinSource.lines()
            .filter { it.isNotBlank() }
            .take(15)
            .joinToString("\n") { "│ $it" }

        val swiftPreview = swiftCode.lines()
            .joinToString("\n") { "│ $it" }

        return """
            |┌─────────────────────────────────────────────────────────────────
            |│ File: $fileName
            |├─────────────────────────────────────────────────────────────────
            |│ Kotlin Source:
            |├─────────────────────────────────────────────────────────────────
            |$kotlinPreview
            |├─────────────────────────────────────────────────────────────────
            |│                              ↓
            |├─────────────────────────────────────────────────────────────────
            |│ Generated Swift:
            |├─────────────────────────────────────────────────────────────────
            |$swiftPreview
            |└─────────────────────────────────────────────────────────────────
            |
        """.trimMargin()
    }
}
