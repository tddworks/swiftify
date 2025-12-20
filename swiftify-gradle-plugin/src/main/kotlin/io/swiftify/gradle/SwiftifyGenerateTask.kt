package io.swiftify.gradle

import io.swiftify.analyzer.KotlinDeclaration
import io.swiftify.analyzer.KotlinDeclarationAnalyzer
import io.swiftify.analyzer.ManifestDeclarationParser
import io.swiftify.generator.ApiNotesGenerator
import io.swiftify.generator.SwiftRuntimeSupport
import io.swiftify.generator.SwiftifyTransformer
import io.swiftify.generator.TransformOptions
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Task to generate Swift code from Kotlin declarations.
 *
 * Usage:
 * ```bash
 * ./gradlew swiftifyGenerate
 * ./gradlew swiftifyGenerateIosArm64
 * ```
 */
abstract class SwiftifyGenerateTask : DefaultTask() {

    /**
     * Output directory for generated Swift files.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Target name (e.g., iosArm64, macosArm64).
     */
    @get:Input
    @get:Optional
    abstract val targetName: Property<String>

    /**
     * Framework name for ApiNotes generation.
     */
    @get:Input
    @get:Optional
    abstract val frameworkName: Property<String>

    /**
     * Whether to generate ApiNotes file.
     */
    @get:Input
    @get:Optional
    abstract val generateApiNotes: Property<Boolean>

    /**
     * Kotlin source files to transform (used in REGEX mode).
     */
    @get:InputFiles
    @get:Optional
    abstract val kotlinSources: ConfigurableFileCollection

    /**
     * Analysis mode: REGEX or KSP.
     */
    @get:Input
    @get:Optional
    abstract val analysisMode: Property<AnalysisMode>

    /**
     * Manifest files from KSP (used in KSP mode).
     */
    @get:InputFiles
    @get:Optional
    abstract val manifestFiles: ListProperty<File>

    private val transformer = SwiftifyTransformer()
    private val analyzer = KotlinDeclarationAnalyzer()
    private val manifestParser = ManifestDeclarationParser()
    private val apiNotesGenerator = ApiNotesGenerator()

    init {
        group = "swiftify"
        description = "Generate Swift code from Kotlin declarations"
        generateApiNotes.convention(true)
        analysisMode.convention(AnalysisMode.REGEX)
    }

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val mode = analysisMode.getOrElse(AnalysisMode.REGEX)
        val target = if (targetName.isPresent) targetName.get() else "default"
        val fwName = frameworkName.orNull ?: project.name.replaceFirstChar { it.uppercase() }
        logger.lifecycle("Swiftify: Generating Swift code for target: $target, framework: $fwName, mode: $mode")

        // Generate with implementations so the code is actually usable
        val options = TransformOptions(
            generateImplementations = true,
            frameworkName = fwName,
        )

        val allDeclarations: List<KotlinDeclaration>
        val generatedFiles = mutableListOf<File>()
        var totalTransformed = 0

        when (mode) {
            AnalysisMode.REGEX -> {
                val sourceFiles = findKotlinSources()
                if (sourceFiles.isEmpty()) {
                    logger.lifecycle("Swiftify: No Kotlin source files found.")
                    return
                }

                val declarationsFromSources = mutableListOf<KotlinDeclaration>()
                sourceFiles.forEach { file ->
                    val source = file.readText()
                    val result = transformer.transform(source, options = options)

                    if (result.declarationsTransformed > 0) {
                        val outputFile = File(outputDir, "${file.nameWithoutExtension}+Swiftify.swift")
                        outputFile.writeText(buildSwiftFile(file.nameWithoutExtension, fwName, result.swiftCode))
                        generatedFiles.add(outputFile)
                        totalTransformed += result.declarationsTransformed
                        declarationsFromSources.addAll(result.declarations)
                        logger.info("Swiftify: Generated ${outputFile.name}")
                    }
                }
                allDeclarations = declarationsFromSources
            }
            AnalysisMode.KSP -> {
                val manifests = manifestFiles.getOrElse(emptyList()).filter { it.exists() }
                if (manifests.isEmpty()) {
                    logger.lifecycle("Swiftify: No manifest files found. Ensure KSP is configured and has run.")
                    return
                }

                logger.lifecycle("Swiftify: Processing ${manifests.size} manifest(s) from KSP")

                // Merge all manifests
                val mergedContent = ManifestMerger().merge(manifests)
                if (mergedContent.isBlank()) {
                    logger.lifecycle("Swiftify: All manifests are empty")
                    return
                }

                // Parse manifest to declarations
                val declarations = manifestParser.parse(mergedContent)
                if (declarations.isEmpty()) {
                    logger.lifecycle("Swiftify: No declarations found in manifests")
                    return
                }

                // Transform declarations to Swift
                // In KSP mode, include sealed classes since user explicitly uses @SwiftEnum
                val kspOptions = options.copy(includeSealedClasses = true)
                val result = transformer.transformDeclarations(declarations, kspOptions)

                if (result.swiftCode.isNotBlank()) {
                    val outputFile = File(outputDir, "Swiftify.swift")
                    outputFile.writeText(buildSwiftFileKsp(fwName, result.swiftCode))
                    generatedFiles.add(outputFile)
                    totalTransformed = result.declarationsTransformed
                    logger.info("Swiftify: Generated ${outputFile.name}")
                }

                allDeclarations = declarations
            }
        }

        // Generate runtime support file (needed for Flow bridging)
        val runtimeFile = File(outputDir, SwiftRuntimeSupport.FILENAME)
        runtimeFile.writeText(SwiftRuntimeSupport.generate(fwName))
        logger.lifecycle("Swiftify: Generated runtime support: ${runtimeFile.absolutePath}")

        // Write combined file if multiple sources (REGEX mode only)
        if (generatedFiles.size > 1) {
            val combinedFile = File(outputDir, "Swiftify.swift")
            val combinedContent = buildCombinedSwiftFile(generatedFiles, fwName)
            combinedFile.writeText(combinedContent)
            logger.lifecycle("Swiftify: Generated combined file: ${combinedFile.absolutePath}")
        }

        // Generate ApiNotes if enabled
        if (generateApiNotes.getOrElse(true) && allDeclarations.isNotEmpty()) {
            val apiNotesFile = File(outputDir, apiNotesGenerator.getFileName(fwName))
            val apiNotesContent = apiNotesGenerator.generate(fwName, allDeclarations)
            apiNotesFile.writeText(apiNotesContent)
            logger.lifecycle("Swiftify: Generated ApiNotes: ${apiNotesFile.absolutePath}")
        }

        logger.lifecycle("Swiftify: Transformed $totalTransformed declarations in mode $mode")
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
            project.file("src/iosMain/kotlin"),
        )

        return srcDirs.filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
    }

    private fun buildSwiftFile(moduleName: String, frameworkName: String, swiftCode: String): String = """
        |// Generated by Swiftify (REGEX mode)
        |// Source: $moduleName.kt
        |// Do not edit - this file is auto-generated
        |
        |import Foundation
        |import $frameworkName
        |
        |$swiftCode
    """.trimMargin()

    private fun buildSwiftFileKsp(frameworkName: String, swiftCode: String): String = """
        |// Generated by Swiftify (KSP mode)
        |// Do not edit - this file is auto-generated
        |
        |import Foundation
        |import $frameworkName
        |
        |$swiftCode
    """.trimMargin()

    private fun buildCombinedSwiftFile(generatedFiles: List<File>, frameworkName: String): String {
        val content = generatedFiles.joinToString("\n\n") { file ->
            "// MARK: - ${file.nameWithoutExtension}\n${file.readText()}"
        }

        return """
            |// Generated by Swiftify
            |// Combined Swift interface file
            |// Do not edit - this file is auto-generated
            |
            |import Foundation
            |import $frameworkName
            |
            |$content
        """.trimMargin()
    }
}
