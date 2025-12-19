package io.swiftify.linker

import java.io.File

/**
 * Compiles Swift source files into object files (.o) and Swift module interfaces.
 *
 * Single Responsibility: Swift compilation to object files and module interfaces.
 */
class SwiftCompiler {

    /**
     * Compiles Swift source files to object files and module interfaces.
     *
     * @param sourceFiles List of Swift source files to compile
     * @param config Compilation configuration
     * @return CompileResult with success/failure status
     */
    fun compile(sourceFiles: List<File>, config: CompileConfig): CompileResult {
        if (sourceFiles.isEmpty()) {
            return CompileResult.Error("No source files provided")
        }

        val missingFiles = sourceFiles.filter { !it.exists() }
        if (missingFiles.isNotEmpty()) {
            return CompileResult.Error("Source files not found: ${missingFiles.joinToString { it.name }}")
        }

        val command = buildCommand(sourceFiles, config)
        val outputFile = getOutputFile(sourceFiles.first(), config)
        val swiftModuleDir = getSwiftModuleDir(config)

        if (config.dryRun) {
            config.logger?.invoke("Would run: ${command.joinToString(" ")}")
            return CompileResult.Success(listOf(outputFile), swiftModuleDir)
        }

        // Ensure output directories exist
        config.outputDirectory?.mkdirs()
        swiftModuleDir.mkdirs()

        return try {
            val process = ProcessBuilder(command)
                .directory(config.workingDirectory ?: sourceFiles.first().parentFile)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                CompileResult.Error("Compilation failed: $output")
            } else {
                config.logger?.invoke("Compiled ${sourceFiles.size} Swift files with module interface")
                CompileResult.Success(listOf(outputFile), swiftModuleDir)
            }
        } catch (e: Exception) {
            CompileResult.Error("Compilation failed: ${e.message}")
        }
    }

    /**
     * Builds the swiftc command for compilation.
     * Exposed for testing.
     */
    fun buildCommand(sourceFiles: List<File>, config: CompileConfig): List<String> {
        val frameworkName = config.frameworkPath.nameWithoutExtension
        val moduleName = "${frameworkName}Swiftify"
        val outputFile = getOutputFile(sourceFiles.first(), config)
        val swiftModuleDir = getSwiftModuleDir(config)

        return buildList {
            add("swiftc")

            // Module configuration
            add("-module-name")
            add(moduleName)

            // Emit object file AND module interface
            add("-emit-object")
            add("-emit-module")
            add("-emit-module-interface")
            add("-enable-library-evolution")
            add("-whole-module-optimization")

            // Module output paths
            add("-emit-module-path")
            add(File(swiftModuleDir, "$moduleName.swiftmodule").absolutePath)

            // Target triple
            add("-target")
            add(config.targetTriple)

            // SDK path
            if (config.sdkPath != null) {
                add("-sdk")
                add(config.sdkPath)
            }

            // Framework search path (parent directory of .framework)
            add("-F")
            add(config.frameworkPath.parentFile.absolutePath)

            // Link against the original Kotlin framework
            add("-framework")
            add(frameworkName)

            // Output file (with WMO, multiple sources -> single .o)
            add("-o")
            add(outputFile.absolutePath)

            // Source files
            sourceFiles.forEach { add(it.absolutePath) }
        }
    }

    private fun getOutputFile(sourceFile: File, config: CompileConfig): File {
        val outputDir = config.outputDirectory ?: sourceFile.parentFile
        return File(outputDir, "${sourceFile.nameWithoutExtension}.o")
    }

    private fun getSwiftModuleDir(config: CompileConfig): File {
        val outputDir = config.outputDirectory ?: File(".")
        return File(outputDir, "swiftmodule")
    }
}

/**
 * Configuration for Swift compilation.
 */
data class CompileConfig(
    /** Path to the framework to link against */
    val frameworkPath: File,
    /** Target triple (e.g., "arm64-apple-macos14.0") */
    val targetTriple: String,
    /** Path to the SDK (e.g., MacOSX.sdk) */
    val sdkPath: String? = null,
    /** Output directory for .o files */
    val outputDirectory: File? = null,
    /** Working directory for compilation */
    val workingDirectory: File? = null,
    /** Dry run mode - don't execute, just return command */
    val dryRun: Boolean = false,
    /** Logger for output */
    val logger: ((String) -> Unit)? = null
)

/**
 * Result of Swift compilation.
 */
sealed class CompileResult {
    /** Successful compilation with list of output object files and Swift module directory */
    data class Success(
        val objectFiles: List<File>,
        val swiftModuleDir: File
    ) : CompileResult()
    /** Compilation failed with error message */
    data class Error(val message: String) : CompileResult()
}
