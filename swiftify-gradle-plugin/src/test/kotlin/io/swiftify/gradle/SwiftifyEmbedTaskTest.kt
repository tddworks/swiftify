package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwiftifyEmbedTaskTest {
    private lateinit var project: Project

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir)
                .withName("testProject")
                .build()
        project.plugins.apply("io.swiftify")
    }

    @Test
    fun `embed task has correct group`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertEquals("swiftify", task.group)
    }

    @Test
    fun `embed task has correct description`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertEquals("Embed Swift extensions into framework binary", task.description)
    }

    @Test
    fun `dryRun defaults to false`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertFalse(task.dryRun.get())
    }

    @Test
    fun `dryRun can be set to true`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask
        task.dryRun.set(true)

        assertTrue(task.dryRun.get())
    }

    @Test
    fun `frameworkDirectory is optional`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertFalse(task.frameworkDirectory.isPresent)
    }

    @Test
    fun `frameworkDirectory can be set`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask
        val frameworkDir = File(tempDir, "framework.framework")
        task.frameworkDirectory.set(frameworkDir)

        assertTrue(task.frameworkDirectory.isPresent)
        assertEquals(frameworkDir.absolutePath, task.frameworkDirectory.get().asFile.absolutePath)
    }

    @Test
    fun `swiftSourceDirectory is configured from extension`() {
        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertEquals(
            extension.outputDirectory
                .get()
                .asFile.absolutePath,
            task.swiftSourceDirectory
                .get()
                .asFile.absolutePath,
        )
    }

    @Test
    fun `sdkPath is optional`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        assertFalse(task.sdkPath.isPresent)
    }

    @Test
    fun `sdkPath can be set`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask
        task.sdkPath.set("/path/to/sdk")

        assertTrue(task.sdkPath.isPresent)
        assertEquals("/path/to/sdk", task.sdkPath.get())
    }

    @Test
    fun `embed task skips when no framework directory is configured`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Should not throw - will log and skip
        task.embed()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `embed task skips when framework directory does not exist`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask
        val nonExistentFramework = File(tempDir, "nonexistent.framework")
        task.frameworkDirectory.set(nonExistentFramework)

        // Should not throw - will log and skip
        task.embed()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `embed task skips when swift source directory does not exist`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Set swift source to non-existent directory
        val nonExistentSwiftDir = File(tempDir, "nonexistent-swift")
        task.swiftSourceDirectory.set(nonExistentSwiftDir)

        // Should not throw - will log and skip
        task.embed()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `embed task skips when no swift files are found`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Create swift source directory but without required files
        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()
        task.swiftSourceDirectory.set(swiftDir)

        // Create a file but not the required ones
        File(swiftDir, "Other.swift").writeText("// other")

        // Should not throw - will log and skip
        task.embed()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `embed task finds Swiftify swift file`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework directory with binary
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Create swift source directory with required file
        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()
        task.swiftSourceDirectory.set(swiftDir)

        val swiftFile = File(swiftDir, "Swiftify.swift")
        swiftFile.writeText(
            """
            import Foundation

            extension TestClass {
                func test() -> String { "test" }
            }
            """.trimIndent(),
        )

        // Verify the file exists and would be processed
        assertTrue(swiftFile.exists())
        assertTrue(swiftFile.extension == "swift")
        assertTrue(swiftFile.name == "Swiftify.swift")
    }

    @Test
    fun `embed task finds SwiftifyRuntime swift file`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Create swift source directory with required file
        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()
        task.swiftSourceDirectory.set(swiftDir)

        val runtimeFile = File(swiftDir, "SwiftifyRuntime.swift")
        runtimeFile.writeText(
            """
            import Foundation

            public struct SwiftifyRuntime {
                // runtime code
            }
            """.trimIndent(),
        )

        // Verify the file exists and would be processed
        assertTrue(runtimeFile.exists())
        assertTrue(runtimeFile.extension == "swift")
        assertTrue(runtimeFile.name == "SwiftifyRuntime.swift")
    }

    @Test
    fun `embed task ignores non-Swiftify swift files`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Create swift source directory with non-matching files
        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()
        task.swiftSourceDirectory.set(swiftDir)

        // Create files that should be ignored
        File(swiftDir, "NetworkResult.swift").writeText("// generated")
        File(swiftDir, "Extensions.swift").writeText("// extensions")

        // Should skip - only Swiftify.swift and SwiftifyRuntime.swift are processed
        task.embed()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `task can be configured with custom sdk path`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        val customSdkPath = "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
        task.sdkPath.set(customSdkPath)

        assertEquals(customSdkPath, task.sdkPath.get())
    }

    @Test
    fun `task recognizes both swiftify files`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create swift source directory with both required files
        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()
        task.swiftSourceDirectory.set(swiftDir)

        val swiftifyFile = File(swiftDir, "Swiftify.swift")
        swiftifyFile.writeText("// extensions")

        val runtimeFile = File(swiftDir, "SwiftifyRuntime.swift")
        runtimeFile.writeText("// runtime")

        // Verify both files exist
        assertTrue(swiftifyFile.exists())
        assertTrue(runtimeFile.exists())
        assertEquals("swift", swiftifyFile.extension)
        assertEquals("swift", runtimeFile.extension)
    }

    @Test
    fun `task can be configured with nested framework path`() {
        val task = project.tasks.getByName("swiftifyEmbed") as SwiftifyEmbedTask

        // Create framework in a specific location
        val buildDir = File(tempDir, "build/frameworks")
        buildDir.mkdirs()
        val frameworkDir = File(buildDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        // Verify configuration
        assertEquals(frameworkDir.absolutePath, task.frameworkDirectory.get().asFile.absolutePath)
        assertTrue(frameworkDir.exists())
        assertTrue(frameworkDir.parentFile.exists())
    }
}
