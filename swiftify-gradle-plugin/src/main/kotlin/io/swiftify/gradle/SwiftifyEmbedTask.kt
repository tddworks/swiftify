package io.swiftify.gradle

import io.swiftify.linker.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Task that embeds generated Swift code into a Kotlin/Native framework binary.
 *
 * This task:
 * 1. Analyzes the framework structure (platform, architecture)
 * 2. Compiles Swift files to object files (.o)
 * 3. Merges object files into the framework binary
 *
 * The result is a self-contained framework with Swift extensions embedded.
 */
abstract class SwiftifyEmbedTask : DefaultTask() {

    /**
     * The framework directory to embed into.
     */
    @get:InputDirectory
    abstract val frameworkDirectory: DirectoryProperty

    /**
     * Directory containing generated Swift files.
     */
    @get:InputDirectory
    abstract val swiftSourceDirectory: DirectoryProperty

    /**
     * SDK path for Swift compilation.
     * If not specified, will attempt to auto-detect.
     */
    @get:Input
    @get:Optional
    abstract val sdkPath: Property<String>

    /**
     * Whether to run in dry-run mode (no actual compilation/merging).
     */
    @get:Input
    @get:Optional
    abstract val dryRun: Property<Boolean>

    private val embedder = SwiftEmbedder()

    init {
        group = "swiftify"
        description = "Embed Swift extensions into framework binary"
        dryRun.convention(false)
    }

    @TaskAction
    fun embed() {
        val frameworkDir = frameworkDirectory.get().asFile
        if (!frameworkDir.exists()) {
            logger.lifecycle("Swiftify: Framework not found at ${frameworkDir.absolutePath}")
            return
        }

        val swiftDir = swiftSourceDirectory.get().asFile
        if (!swiftDir.exists()) {
            logger.lifecycle("Swiftify: No Swift source directory found at ${swiftDir.absolutePath}")
            return
        }

        val swiftFiles = swiftDir.listFiles { file -> file.extension == "swift" }?.toList()
        if (swiftFiles.isNullOrEmpty()) {
            logger.lifecycle("Swiftify: No Swift files to embed")
            return
        }

        logger.lifecycle("Swiftify: Embedding ${swiftFiles.size} Swift files into ${frameworkDir.name}")

        val config = EmbedConfig(
            sdkPath = sdkPath.orNull ?: detectSdkPath(frameworkDir),
            workingDirectory = File(frameworkDir.parentFile, ".swiftify-build"),
            dryRun = dryRun.getOrElse(false),
            logger = { msg -> logger.lifecycle("Swiftify: $msg") }
        )

        val result = embedder.embed(frameworkDir, swiftFiles, config)

        when (result) {
            is EmbedResult.Success -> {
                logger.lifecycle("Swiftify: Successfully embedded ${result.swiftFilesEmbedded} Swift files into ${result.frameworkName}")
            }
            is EmbedResult.Error -> {
                throw org.gradle.api.GradleException("Swiftify embedding failed: ${result.message}")
            }
        }
    }

    /**
     * Auto-detect SDK path based on framework platform.
     */
    private fun detectSdkPath(frameworkDir: File): String? {
        val path = frameworkDir.absolutePath.lowercase()

        val platform = when {
            path.contains("ios") && path.contains("simulator") -> "iPhoneSimulator"
            path.contains("ios") -> "iPhoneOS"
            path.contains("watchos") && path.contains("simulator") -> "WatchSimulator"
            path.contains("watchos") -> "WatchOS"
            path.contains("tvos") && path.contains("simulator") -> "AppleTVSimulator"
            path.contains("tvos") -> "AppleTVOS"
            else -> "MacOSX"
        }

        return try {
            val process = ProcessBuilder("xcrun", "--sdk", platform.lowercase(), "--show-sdk-path")
                .redirectErrorStream(true)
                .start()
            val sdkPath = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && sdkPath.isNotEmpty()) sdkPath else null
        } catch (e: Exception) {
            logger.debug("Swiftify: Could not auto-detect SDK path: ${e.message}")
            null
        }
    }
}
