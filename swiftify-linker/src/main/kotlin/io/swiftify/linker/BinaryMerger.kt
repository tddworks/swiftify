package io.swiftify.linker

import java.io.File

/**
 * Merges Swift object files into the framework binary.
 *
 * Single Responsibility: Binary merging using ld.
 */
class BinaryMerger {
    /**
     * Merges object files into the framework binary.
     *
     * @param objectFiles List of compiled .o files to merge
     * @param config Merge configuration
     * @return MergeResult with success/failure status
     */
    fun merge(
        objectFiles: List<File>,
        config: MergeConfig,
    ): MergeResult {
        if (objectFiles.isEmpty()) {
            return MergeResult.Error("No object files provided")
        }

        if (!config.originalBinary.exists()) {
            return MergeResult.Error("Original binary not found: ${config.originalBinary.absolutePath}")
        }

        val missingFiles = objectFiles.filter { !it.exists() }
        if (missingFiles.isNotEmpty()) {
            return MergeResult.Error("Object files not found: ${missingFiles.joinToString { it.name }}")
        }

        val command = buildCommand(objectFiles, config)
        val outputFile = config.outputBinary ?: config.originalBinary

        if (config.dryRun) {
            config.logger?.invoke("Would run: ${command.joinToString(" ")}")
            return MergeResult.Success(outputFile)
        }

        return try {
            // Create temp output to avoid overwriting original during merge
            val tempOutput = File(config.originalBinary.parentFile, "${config.originalBinary.name}.merged")

            val process =
                ProcessBuilder(command)
                    .directory(config.originalBinary.parentFile)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                tempOutput.delete()
                MergeResult.Error("Merge failed: $output")
            } else {
                // Replace original with merged binary
                if (config.outputBinary == null) {
                    config.originalBinary.delete()
                    tempOutput.renameTo(config.originalBinary)
                }
                config.logger?.invoke("Merged ${objectFiles.size} object files into binary")
                MergeResult.Success(outputFile)
            }
        } catch (e: Exception) {
            MergeResult.Error("Merge failed: ${e.message}")
        }
    }

    /**
     * Builds the ld command for merging.
     * Exposed for testing.
     */
    fun buildCommand(
        objectFiles: List<File>,
        config: MergeConfig,
    ): List<String> {
        val arch = extractArchitecture(config.targetTriple)
        val outputFile =
            config.outputBinary
                ?: File(config.originalBinary.parentFile, "${config.originalBinary.name}.merged")

        return buildList {
            add("ld")

            // Relocatable output (merge without linking)
            add("-r")

            // Architecture
            add("-arch")
            add(arch)

            // Original binary
            add(config.originalBinary.absolutePath)

            // Object files
            objectFiles.forEach { add(it.absolutePath) }

            // Output
            add("-o")
            add(outputFile.absolutePath)
        }
    }

    private fun extractArchitecture(targetTriple: String): String = when {
        targetTriple.startsWith("arm64") -> "arm64"
        targetTriple.startsWith("x86_64") -> "x86_64"
        targetTriple.startsWith("armv7") -> "armv7"
        else -> "arm64" // Default
    }
}

/**
 * Configuration for binary merging.
 */
data class MergeConfig(
    /** Original framework binary to merge into */
    val originalBinary: File,
    /** Target triple for architecture detection */
    val targetTriple: String,
    /** Output binary path (defaults to replacing original) */
    val outputBinary: File? = null,
    /** Dry run mode - don't execute, just return command */
    val dryRun: Boolean = false,
    /** Logger for output */
    val logger: ((String) -> Unit)? = null,
)

/**
 * Result of binary merging.
 */
sealed class MergeResult {
    /** Successful merge with output binary path */
    data class Success(
        val outputBinary: File,
    ) : MergeResult()

    /** Merge failed with error message */
    data class Error(
        val message: String,
    ) : MergeResult()
}
