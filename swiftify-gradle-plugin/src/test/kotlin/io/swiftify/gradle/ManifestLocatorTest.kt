package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestLocatorTest {
    private lateinit var project: Project

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir)
                .build()
    }

    @Test
    fun `locateManifests returns empty list when KSP directory does not exist`() {
        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertTrue(manifests.isEmpty())
    }

    @Test
    fun `locateManifests finds single manifest in jvm target`() {
        // Create KSP output structure for JVM
        val manifestDir = File(tempDir, "build/generated/ksp/jvm/jvmMain/resources")
        manifestDir.mkdirs()
        val manifestFile = File(manifestDir, "swiftify-manifest.txt")
        manifestFile.writeText("[sealed:Test]")

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertEquals(1, manifests.size)
        assertEquals("swiftify-manifest.txt", manifests.first().name)
    }

    @Test
    fun `locateManifests finds manifest in iosArm64 target`() {
        // Create KSP output structure for iOS
        val manifestDir = File(tempDir, "build/generated/ksp/iosArm64/iosArm64Main/resources")
        manifestDir.mkdirs()
        val manifestFile = File(manifestDir, "swiftify-manifest.txt")
        manifestFile.writeText("[sealed:Test]")

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertEquals(1, manifests.size)
    }

    @Test
    fun `locateManifests finds manifests from multiple targets`() {
        // Create JVM manifest
        val jvmManifestDir = File(tempDir, "build/generated/ksp/jvm/jvmMain/resources")
        jvmManifestDir.mkdirs()
        File(jvmManifestDir, "swiftify-manifest.txt").writeText("[sealed:Test1]")

        // Create iOS manifest
        val iosManifestDir = File(tempDir, "build/generated/ksp/iosArm64/iosArm64Main/resources")
        iosManifestDir.mkdirs()
        File(iosManifestDir, "swiftify-manifest.txt").writeText("[sealed:Test2]")

        // Create macOS manifest
        val macosManifestDir = File(tempDir, "build/generated/ksp/macosArm64/macosArm64Main/resources")
        macosManifestDir.mkdirs()
        File(macosManifestDir, "swiftify-manifest.txt").writeText("[sealed:Test3]")

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertEquals(3, manifests.size)
    }

    @Test
    fun `locateManifests returns deterministic order`() {
        // Create multiple manifests in different directories
        listOf("jvm/jvmMain", "iosArm64/iosArm64Main", "macosArm64/macosArm64Main").forEach { path ->
            val dir = File(tempDir, "build/generated/ksp/$path/resources")
            dir.mkdirs()
            File(dir, "swiftify-manifest.txt").writeText("[sealed:Test]")
        }

        val locator = ManifestLocator(project)
        val manifests1 = locator.locateManifests()
        val manifests2 = locator.locateManifests()

        // Should return same order every time (sorted by path)
        assertEquals(
            manifests1.map { it.absolutePath },
            manifests2.map { it.absolutePath },
        )
    }

    @Test
    fun `locateManifests ignores non-swiftify manifest files`() {
        // Create KSP output with swiftify manifest
        val manifestDir = File(tempDir, "build/generated/ksp/jvm/jvmMain/resources")
        manifestDir.mkdirs()
        File(manifestDir, "swiftify-manifest.txt").writeText("[sealed:Test]")

        // Create other KSP output files that should be ignored
        File(manifestDir, "other-manifest.txt").writeText("other content")
        File(manifestDir, "swiftify-manifest.json").writeText("{}")

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertEquals(1, manifests.size)
        assertEquals("swiftify-manifest.txt", manifests.first().name)
    }

    @Test
    fun `locateManifests ignores empty KSP directory`() {
        // Create empty KSP output structure
        val kspDir = File(tempDir, "build/generated/ksp")
        kspDir.mkdirs()

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertTrue(manifests.isEmpty())
    }

    @Test
    fun `locateManifests handles nested directory structures`() {
        // Create deeply nested structure (e.g., commonMain in addition to target-specific)
        val jvmDir = File(tempDir, "build/generated/ksp/jvm/jvmMain/resources")
        jvmDir.mkdirs()
        File(jvmDir, "swiftify-manifest.txt").writeText("[sealed:Test1]")

        // Some KSP setups might have different structures
        val commonDir = File(tempDir, "build/generated/ksp/metadata/commonMain/resources")
        commonDir.mkdirs()
        File(commonDir, "swiftify-manifest.txt").writeText("[sealed:Test2]")

        val locator = ManifestLocator(project)
        val manifests = locator.locateManifests()

        assertEquals(2, manifests.size)
    }
}
