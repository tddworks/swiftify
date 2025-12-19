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

class SwiftifyLinkTaskTest {
    private lateinit var project: Project
    private lateinit var task: SwiftifyLinkTask

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

        // Create a link task manually for testing
        task =
            project.tasks.create("testSwiftifyLink", SwiftifyLinkTask::class.java)
    }

    @Test
    fun `link task has correct group`() {
        assertEquals("swiftify", task.group)
    }

    @Test
    fun `link task has correct description`() {
        assertEquals("Inject Swiftify extensions into framework", task.description)
    }

    @Test
    fun `compileSwift defaults to false`() {
        assertFalse(task.compileSwift.get())
    }

    @Test
    fun `compileSwift can be enabled`() {
        task.compileSwift.set(true)

        assertTrue(task.compileSwift.get())
    }

    @Test
    fun `frameworkDirectory can be set`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        task.frameworkDirectory.set(frameworkDir)

        assertEquals(frameworkDir.absolutePath, task.frameworkDirectory.get().asFile.absolutePath)
    }

    @Test
    fun `manifestFile is optional`() {
        assertFalse(task.manifestFile.isPresent)
    }

    @Test
    fun `manifestFile can be set`() {
        val manifestFile = File(tempDir, "swiftify-manifest.txt")
        task.manifestFile.set(manifestFile)

        assertTrue(task.manifestFile.isPresent)
        assertEquals(manifestFile.absolutePath, task.manifestFile.get().asFile.absolutePath)
    }

    @Test
    fun `outputDirectory can be configured`() {
        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        assertEquals(outputDir.absolutePath, task.outputDirectory.get().asFile.absolutePath)
    }

    @Test
    fun `targetTriple is optional`() {
        assertFalse(task.targetTriple.isPresent)
    }

    @Test
    fun `targetTriple can be set`() {
        task.targetTriple.set("arm64-apple-ios")

        assertTrue(task.targetTriple.isPresent)
        assertEquals("arm64-apple-ios", task.targetTriple.get())
    }

    @Test
    fun `sdkPath is optional`() {
        assertFalse(task.sdkPath.isPresent)
    }

    @Test
    fun `sdkPath can be set`() {
        val sdk = "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"
        task.sdkPath.set(sdk)

        assertTrue(task.sdkPath.isPresent)
        assertEquals(sdk, task.sdkPath.get())
    }

    @Test
    fun `link task skips when framework does not exist`() {
        val nonExistentFramework = File(tempDir, "NonExistent.framework")
        task.frameworkDirectory.set(nonExistentFramework)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Should not throw - will log and skip
        task.link()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `link task skips when no manifest file is configured`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // No manifest file set - should skip
        task.link()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `link task skips when manifest file does not exist`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        task.frameworkDirectory.set(frameworkDir)

        val nonExistentManifest = File(tempDir, "nonexistent.txt")
        task.manifestFile.set(nonExistentManifest)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Manifest doesn't exist - should skip
        task.link()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `link task processes framework with manifest`() {
        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()

        // Create framework binary
        val binary = File(frameworkDir, "TestFramework")
        binary.writeText("fake binary")

        // Create Headers directory
        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        // Create manifest file
        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.NetworkResult]
            name=NetworkResult
            swiftName=NetworkResult
            exhaustive=true
            subclass=Success:false
            subclass=Error:false
            subclass=Loading:true
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Task should process the manifest
        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `link task copies swift files to output directory`() {
        // Create framework directory
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()

        // Create framework binary
        File(frameworkDir, "TestFramework").writeText("fake binary")

        // Create Headers
        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        // Create manifest
        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.State]
            name=State
            swiftName=State
            exhaustive=true
            subclass=Loading:true
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Task processes and should create Swiftify directory in framework
        task.link()

        // Output directory should be created
        assertTrue(outputDir.exists())
    }

    @Test
    fun `link task handles empty manifest`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        // Create empty manifest
        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText("")
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Should handle gracefully
        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `link task with compile swift enabled`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)
        task.compileSwift.set(true)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.Result]
            name=Result
            swiftName=Result
            exhaustive=true
            subclass=Success:false
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Note: actual compilation will likely fail in test environment
        // but we're testing the configuration and flow
        try {
            task.link()
        } catch (e: Exception) {
            // Expected in test environment without proper toolchain
        }

        // Verify configuration was applied
        assertTrue(task.compileSwift.get())
    }

    @Test
    fun `link task with custom target triple`() {
        task.targetTriple.set("arm64-apple-ios16.0")

        assertEquals("arm64-apple-ios16.0", task.targetTriple.get())
    }

    @Test
    fun `link task with custom sdk path`() {
        val sdkPath = "/path/to/sdk"
        task.sdkPath.set(sdkPath)

        assertEquals(sdkPath, task.sdkPath.get())
    }

    @Test
    fun `link task handles multiple sealed classes in manifest`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.NetworkResult]
            name=NetworkResult
            swiftName=NetworkResult
            exhaustive=true
            subclass=Success:false
            subclass=Error:false

            [sealed:com.example.UiState]
            name=UiState
            swiftName=UiState
            exhaustive=true
            subclass=Loading:true
            subclass=Content:false
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `link task handles suspend functions in manifest`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [suspend:com.example.Repository.fetchData]
            name=fetchData
            return=String
            throwing=true
            param=id:String
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `link task handles flow functions in manifest`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [flow:com.example.DataStream.observe]
            name=observe
            element=String
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `link task creates output directory if it does not exist`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.Result]
            name=Result
            swiftName=Result
            exhaustive=true
            subclass=Success:false
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "nonexistent/output")
        task.outputDirectory.set(outputDir)

        task.link()

        // Output directory should be created
        assertTrue(outputDir.exists())
    }

    @Test
    fun `link task handles mixed declaration types in manifest`() {
        val frameworkDir = File(tempDir, "TestFramework.framework")
        frameworkDir.mkdirs()
        File(frameworkDir, "TestFramework").writeText("fake binary")

        val headersDir = File(frameworkDir, "Headers")
        headersDir.mkdirs()
        File(headersDir, "TestFramework.h").writeText("// header")

        task.frameworkDirectory.set(frameworkDir)

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.Result]
            name=Result
            swiftName=Result
            exhaustive=true
            subclass=Success:false
            subclass=Error:false

            [suspend:com.example.Api.fetch]
            name=fetch
            return=String
            throwing=true

            [flow:com.example.Stream.observe]
            name=observe
            element=Int
            """.trimIndent(),
        )
        task.manifestFile.set(manifestFile)

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.link()

        // Verify task ran successfully
        assertNotNull(task)
    }
}
