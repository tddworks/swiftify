package io.swiftify.linker

import java.io.File

/**
 * Analyzes a Kotlin/Native framework to extract metadata needed for Swift embedding.
 *
 * Single Responsibility: Extract framework information (name, binary, platform, arch).
 */
class FrameworkAnalyzer {
    /**
     * Analyzes a framework and returns its information.
     *
     * @param frameworkDir The .framework directory
     * @return FrameworkInfo with extracted metadata
     * @throws FrameworkAnalysisException if framework is invalid
     */
    fun analyze(frameworkDir: File): FrameworkInfo = analyzeOrNull(frameworkDir)
        ?: throw FrameworkAnalysisException("Invalid framework: ${frameworkDir.absolutePath}")

    /**
     * Analyzes a framework and returns its information, or null if invalid.
     *
     * @param frameworkDir The .framework directory
     * @return FrameworkInfo or null if framework doesn't exist or is invalid
     */
    fun analyzeOrNull(frameworkDir: File): FrameworkInfo? {
        if (!frameworkDir.exists() || !frameworkDir.isDirectory) {
            return null
        }

        val name = extractFrameworkName(frameworkDir) ?: return null
        val binaryPath = locateBinary(frameworkDir, name) ?: return null

        if (!binaryPath.exists()) {
            return null
        }

        val platform = detectPlatform(frameworkDir)
        val arch = detectArchitecture(frameworkDir)
        val deploymentTarget = detectDeploymentTarget(frameworkDir, platform)
        val targetTriple = buildTargetTriple(platform, arch, deploymentTarget)

        return FrameworkInfo(
            name = name,
            binaryPath = binaryPath,
            platform = platform,
            targetTriple = targetTriple,
        )
    }

    private fun extractFrameworkName(frameworkDir: File): String? {
        val dirName = frameworkDir.name
        if (!dirName.endsWith(".framework")) {
            return null
        }
        return dirName.removeSuffix(".framework")
    }

    private fun locateBinary(
        frameworkDir: File,
        name: String,
    ): File? {
        // Primary location: direct child with framework name
        val directBinary = File(frameworkDir, name)
        if (directBinary.exists()) {
            return directBinary
        }

        // Alternative: Versions/A/<name> (older framework structure)
        val versionedBinary = File(frameworkDir, "Versions/A/$name")
        if (versionedBinary.exists()) {
            return versionedBinary
        }

        return null
    }

    private fun detectPlatform(frameworkDir: File): Platform {
        val path = frameworkDir.absolutePath.lowercase()

        return when {
            path.contains("iosarm64") || path.contains("iossimulator") ||
                path.contains("iosx64") || path.contains("/ios") -> Platform.IOS
            path.contains("watchosarm") || path.contains("watchossimulator") ||
                path.contains("watchosx64") || path.contains("/watchos") -> Platform.WATCHOS
            path.contains("tvosarm64") || path.contains("tvossimulator") ||
                path.contains("tvosx64") || path.contains("/tvos") -> Platform.TVOS
            path.contains("macosarm64") || path.contains("macosx64") ||
                path.contains("/macos") -> Platform.MACOS
            else -> Platform.MACOS // Default to macOS
        }
    }

    private fun detectArchitecture(frameworkDir: File): Architecture {
        val path = frameworkDir.absolutePath.lowercase()

        return when {
            path.contains("arm64") -> Architecture.ARM64
            path.contains("x64") || path.contains("x86_64") -> Architecture.X64
            path.contains("arm32") -> Architecture.ARM32
            else -> Architecture.ARM64 // Default to arm64
        }
    }

    /**
     * Detect deployment target from framework's Info.plist.
     */
    private fun detectDeploymentTarget(
        frameworkDir: File,
        platform: Platform,
    ): String {
        // Try to read from Info.plist (check multiple locations)
        val possiblePaths =
            listOf(
                File(frameworkDir, "Info.plist"),
                File(frameworkDir, "Resources/Info.plist"),
                File(frameworkDir, "Versions/A/Resources/Info.plist"),
            )
        val infoPlist = possiblePaths.firstOrNull { it.exists() }
        if (infoPlist != null) {
            try {
                val content = infoPlist.readText()
                // Look for MinimumOSVersion or similar keys
                val versionRegex =
                    when (platform) {
                        Platform.MACOS -> Regex("""<key>LSMinimumSystemVersion</key>\s*<string>([^<]+)</string>""")
                        else -> Regex("""<key>MinimumOSVersion</key>\s*<string>([^<]+)</string>""")
                    }
                val match = versionRegex.find(content)
                if (match != null) {
                    return match.groupValues[1]
                }
            } catch (e: Exception) {
                // Fall through to defaults
            }
        }

        // Sensible defaults if not found
        return when (platform) {
            Platform.IOS -> "14.0"
            Platform.MACOS -> "11.0"
            Platform.WATCHOS -> "7.0"
            Platform.TVOS -> "14.0"
        }
    }

    private fun buildTargetTriple(
        platform: Platform,
        arch: Architecture,
        deploymentTarget: String,
    ): String {
        val archString =
            when (arch) {
                Architecture.ARM64 -> "arm64"
                Architecture.X64 -> "x86_64"
                Architecture.ARM32 -> "armv7"
            }

        val osString =
            when (platform) {
                Platform.IOS -> "apple-ios$deploymentTarget"
                Platform.MACOS -> "apple-macos$deploymentTarget"
                Platform.WATCHOS -> "apple-watchos$deploymentTarget"
                Platform.TVOS -> "apple-tvos$deploymentTarget"
            }

        return "$archString-$osString"
    }
}

/**
 * Information about an analyzed framework.
 */
data class FrameworkInfo(
    /** Framework name (without .framework extension) */
    val name: String,
    /** Path to the framework binary */
    val binaryPath: File,
    /** Target platform */
    val platform: Platform,
    /** Target triple for Swift compiler (e.g., "arm64-apple-macos") */
    val targetTriple: String,
)

/**
 * Supported Apple platforms.
 */
enum class Platform {
    IOS,
    MACOS,
    WATCHOS,
    TVOS,
}

/**
 * CPU architectures.
 */
enum class Architecture {
    ARM64,
    X64,
    ARM32,
}

/**
 * Exception thrown when framework analysis fails.
 */
class FrameworkAnalysisException(
    message: String,
) : Exception(message)
