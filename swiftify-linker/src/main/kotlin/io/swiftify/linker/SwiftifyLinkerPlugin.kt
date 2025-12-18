package io.swiftify.linker

import java.io.File

/**
 * Swiftify Linker Plugin.
 *
 * This component handles the injection of generated Swift code into
 * the Kotlin/Native framework during the link phase.
 *
 * The linker:
 * 1. Reads generated Swift files from the Swiftify output directory
 * 2. Compiles Swift code using the Swift compiler
 * 3. Links the compiled Swift into the framework
 * 4. Updates the framework headers if needed
 */
class SwiftifyLinkerPlugin(
    private val config: SwiftifyLinkerConfig
) {

    /**
     * Injects Swift code into a Kotlin/Native framework.
     *
     * @param frameworkDir The framework directory (e.g., MyApp.framework)
     * @param swiftSourceDir Directory containing generated Swift files
     * @return Result of the injection process
     */
    fun inject(frameworkDir: File, swiftSourceDir: File): InjectionResult {
        if (!frameworkDir.exists() || !frameworkDir.isDirectory) {
            return InjectionResult.Error("Framework directory does not exist: ${frameworkDir.absolutePath}")
        }

        val swiftFiles = swiftSourceDir.listFiles { file -> file.extension == "swift" }
            ?: return InjectionResult.Success(0, "No Swift files to inject")

        if (swiftFiles.isEmpty()) {
            return InjectionResult.Success(0, "No Swift files to inject")
        }

        return try {
            val compiledCount = compileAndLink(frameworkDir, swiftFiles.toList())
            InjectionResult.Success(compiledCount, "Successfully injected ${compiledCount} Swift files")
        } catch (e: Exception) {
            InjectionResult.Error("Failed to inject Swift code: ${e.message}")
        }
    }

    private fun compileAndLink(frameworkDir: File, swiftFiles: List<File>): Int {
        val swiftDir = File(frameworkDir, "Swiftify")
        swiftDir.mkdirs()

        // Copy Swift files to framework
        swiftFiles.forEach { file ->
            file.copyTo(File(swiftDir, file.name), overwrite = true)
        }

        if (config.compileSwift) {
            compileSwiftCode(frameworkDir, swiftFiles)
        }

        return swiftFiles.size
    }

    private fun compileSwiftCode(frameworkDir: File, swiftFiles: List<File>) {
        val frameworkName = frameworkDir.nameWithoutExtension
        val moduleName = frameworkName

        // Build Swift compilation command
        val command = buildList {
            add("swiftc")
            add("-module-name")
            add("${moduleName}Swift")
            add("-emit-library")
            add("-emit-module")

            // Link against the Kotlin framework
            add("-F")
            add(frameworkDir.parentFile.absolutePath)
            add("-framework")
            add(frameworkName)

            // SDK and target settings
            if (config.targetTriple != null) {
                add("-target")
                add(config.targetTriple)
            }

            if (config.sdkPath != null) {
                add("-sdk")
                add(config.sdkPath)
            }

            // Output
            add("-o")
            add(File(frameworkDir, "Swiftify/libSwiftify.dylib").absolutePath)

            // Source files
            swiftFiles.forEach { add(it.absolutePath) }
        }

        if (config.dryRun) {
            config.logger?.invoke("Would run: ${command.joinToString(" ")}")
            return
        }

        val process = ProcessBuilder(command)
            .directory(frameworkDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw SwiftCompilationException("Swift compilation failed: $output")
        }

        config.logger?.invoke("Swift compilation successful")
    }
}

/**
 * Configuration for the Swiftify linker.
 */
data class SwiftifyLinkerConfig(
    /**
     * Whether to compile Swift code (requires Xcode/Swift toolchain).
     */
    val compileSwift: Boolean = false,

    /**
     * Target triple (e.g., "arm64-apple-ios14.0").
     */
    val targetTriple: String? = null,

    /**
     * Path to the SDK.
     */
    val sdkPath: String? = null,

    /**
     * Whether to perform a dry run (don't execute commands).
     */
    val dryRun: Boolean = false,

    /**
     * Logger function for output.
     */
    val logger: ((String) -> Unit)? = null
)

/**
 * Result of Swift injection.
 */
sealed class InjectionResult {
    data class Success(val filesInjected: Int, val message: String) : InjectionResult()
    data class Error(val message: String) : InjectionResult()
}

/**
 * Exception thrown when Swift compilation fails.
 */
class SwiftCompilationException(message: String) : Exception(message)
