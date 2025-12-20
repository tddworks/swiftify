package io.swiftify.gradle

import org.gradle.api.Project
import java.io.File

/**
 * Locates KSP-generated manifest files across all targets in a KMP project.
 *
 * KSP outputs manifests to target-specific directories like:
 * - build/generated/ksp/jvm/jvmMain/resources/swiftify-manifest.txt
 * - build/generated/ksp/iosArm64/iosArm64Main/resources/swiftify-manifest.txt
 *
 * This class auto-detects all such manifests for merging.
 */
class ManifestLocator(
    private val project: Project,
) {
    companion object {
        private const val MANIFEST_FILENAME = "swiftify-manifest.txt"
        private const val KSP_OUTPUT_BASE = "generated/ksp"
    }

    /**
     * Locates all swiftify-manifest.txt files in KSP output directories.
     * Returns empty list if none found.
     */
    fun locateManifests(): List<File> {
        val buildDir = project.layout.buildDirectory.get().asFile
        val kspDir = File(buildDir, KSP_OUTPUT_BASE)

        if (!kspDir.exists()) {
            project.logger.debug("Swiftify: KSP output directory not found: ${kspDir.absolutePath}")
            return emptyList()
        }

        // Search recursively for swiftify-manifest.txt
        val manifests =
            kspDir
                .walkTopDown()
                .filter { it.isFile && it.name == MANIFEST_FILENAME }
                .sortedBy { it.absolutePath } // Deterministic ordering
                .toList()

        if (manifests.isNotEmpty()) {
            project.logger.info("Swiftify: Found ${manifests.size} manifest(s):")
            manifests.forEach {
                project.logger.info("  - ${it.relativeTo(buildDir).path}")
            }
        }

        return manifests
    }
}
