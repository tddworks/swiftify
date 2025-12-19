package io.swiftify.linker

import java.io.File

/**
 * Installs Swift module files into a framework's Modules directory.
 *
 * Single Responsibility: Copy Swift module interface files to make extensions visible.
 *
 * After embedding Swift code into a framework binary, the Swift API is not automatically
 * visible to consumers. This class copies the compiled .swiftmodule files into the
 * framework's Modules directory so that Swift consumers can import the extensions.
 */
class SwiftModuleInstaller {

    /**
     * Installs Swift module files into the framework.
     *
     * @param swiftModuleDir Directory containing compiled Swift module files
     * @param frameworkDir The .framework directory to install into
     * @param config Installation configuration
     * @return InstallResult with success/failure status
     */
    fun install(
        swiftModuleDir: File,
        frameworkDir: File,
        config: InstallConfig = InstallConfig()
    ): InstallResult {
        if (!swiftModuleDir.exists() || !swiftModuleDir.isDirectory) {
            return InstallResult.Error("Swift module directory not found: ${swiftModuleDir.absolutePath}")
        }

        val modulesDir = resolveModulesDir(frameworkDir)
        if (!modulesDir.exists()) {
            return InstallResult.Error("Framework Modules directory not found: ${modulesDir.absolutePath}")
        }

        if (config.dryRun) {
            config.logger?.invoke("Would copy Swift module from ${swiftModuleDir.absolutePath} to ${modulesDir.absolutePath}")
            return InstallResult.Success(modulesDir)
        }

        // Convert target triple to architecture prefix for module files
        // e.g., "arm64-apple-macos11.0" -> "arm64-apple-macos"
        val archPrefix = config.targetTriple?.let { triple ->
            // Remove version number from OS part: arm64-apple-macos11.0 -> arm64-apple-macos
            triple.replace(Regex("(macos|ios|watchos|tvos)[0-9.]+"), "$1")
        }

        return try {
            // Copy all Swift module files to framework's Modules directory
            val copiedFiles = mutableListOf<File>()
            swiftModuleDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.endsWith(".swiftmodule")) {
                    // This is a .swiftmodule bundle - need to rename inner files
                    val destBundle = File(modulesDir, file.name)
                    destBundle.mkdirs()

                    // Copy and rename files inside the bundle with architecture prefix
                    file.listFiles()?.forEach { innerFile ->
                        val destName = if (archPrefix != null) {
                            // Rename ModuleName.xxx to arch.xxx
                            val ext = innerFile.name.substringAfterLast(".")
                            "$archPrefix.$ext"
                        } else {
                            innerFile.name
                        }
                        val destFile = File(destBundle, destName)
                        innerFile.copyTo(destFile, overwrite = true)
                        config.logger?.invoke("Installed: ${file.name}/$destName")
                    }
                    copiedFiles.add(destBundle)
                } else {
                    val destFile = File(modulesDir, file.name)
                    file.copyTo(destFile, overwrite = true)
                    copiedFiles.add(destFile)
                    config.logger?.invoke("Installed: ${file.name}")
                }
            }

            if (copiedFiles.isEmpty()) {
                return InstallResult.Error("No Swift module files found to install")
            }

            // Update module.modulemap to include the Swift module
            val modulemapFile = File(modulesDir, "module.modulemap")
            if (modulemapFile.exists()) {
                val frameworkName = frameworkDir.name.removeSuffix(".framework")
                val swiftModuleName = "${frameworkName}Swiftify"
                val modulemapContent = modulemapFile.readText()

                // Only add if not already present
                if (!modulemapContent.contains("module \"$swiftModuleName\"")) {
                    val swiftModuleDecl = """

module "$swiftModuleName" {
}
"""
                    modulemapFile.appendText(swiftModuleDecl)
                    config.logger?.invoke("Updated module.modulemap with $swiftModuleName")
                }
            }

            config.logger?.invoke("Installed ${copiedFiles.size} Swift module files")
            InstallResult.Success(modulesDir)
        } catch (e: Exception) {
            InstallResult.Error("Failed to install Swift module: ${e.message}")
        }
    }

    /**
     * Resolves the Modules directory within a framework.
     * Handles both flat and versioned framework structures.
     */
    private fun resolveModulesDir(frameworkDir: File): File {
        // Check for symlink (common in macOS frameworks)
        val modulesSymlink = File(frameworkDir, "Modules")
        if (modulesSymlink.exists()) {
            return modulesSymlink
        }

        // Check for versioned structure
        val versionedModules = File(frameworkDir, "Versions/A/Modules")
        if (versionedModules.exists()) {
            return versionedModules
        }

        // Fall back to direct Modules directory
        return modulesSymlink
    }
}

/**
 * Configuration for Swift module installation.
 */
data class InstallConfig(
    /** Target triple for architecture-specific naming (e.g., "arm64-apple-macos11.0") */
    val targetTriple: String? = null,
    /** Dry run mode - don't execute, just report */
    val dryRun: Boolean = false,
    /** Logger for output */
    val logger: ((String) -> Unit)? = null
)

/**
 * Result of Swift module installation.
 */
sealed class InstallResult {
    /** Successful installation */
    data class Success(val modulesDir: File) : InstallResult()
    /** Installation failed */
    data class Error(val message: String) : InstallResult()
}
