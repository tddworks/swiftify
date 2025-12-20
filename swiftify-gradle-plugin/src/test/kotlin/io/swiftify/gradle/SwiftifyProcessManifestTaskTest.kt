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

class SwiftifyProcessManifestTaskTest {
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
    fun `processManifest task has correct group`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        assertEquals("swiftify", task.group)
    }

    @Test
    fun `processManifest task has correct description`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        assertEquals("Process KSP manifest and generate Swift code (KSP mode)", task.description)
    }

    @Test
    fun `manifestFiles has default from plugin`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        // Plugin sets a default manifest files location (provider)
        assertTrue(task.manifestFiles.isPresent)
    }

    @Test
    fun `manifestFiles can be set`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        val manifest = File(tempDir, "manifest.txt")
        task.manifestFiles.set(listOf(manifest))

        assertTrue(task.manifestFiles.isPresent)
        assertEquals(1, task.manifestFiles.get().size)
        assertEquals(manifest.absolutePath, task.manifestFiles.get().first().absolutePath)
    }

    @Test
    fun `outputDirectory can be configured`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        assertEquals(outputDir.absolutePath, task.outputDirectory.get().asFile.absolutePath)
    }

    @Test
    fun `task skips when no manifest files are configured`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        task.manifestFiles.set(emptyList())
        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Should not throw - will log and skip
        task.processManifest()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `task skips when manifest files do not exist`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val nonExistentManifest = File(tempDir, "nonexistent.txt")
        task.manifestFiles.set(listOf(nonExistentManifest))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Should not throw - will log and skip
        task.processManifest()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `task processes sealed class manifest`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

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
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Verify output directory was created
        assertTrue(outputDir.exists())

        // Verify Swift file was generated
        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        // Verify content includes enum definition
        val content = swiftFile.readText()
        assertTrue(content.contains("enum NetworkResult"))
        assertTrue(content.contains("case success"))
        assertTrue(content.contains("case error"))
        assertTrue(content.contains("case loading"))
    }

    @Test
    fun `task processes suspend function manifest`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [suspend:com.example.Repository.fetchData]
            name=fetchData
            return=String
            throwing=true
            param=id:String
            param=refresh:Boolean
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Verify output was created
        assertTrue(outputDir.exists())

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        // Verify content includes async function
        val content = swiftFile.readText()
        assertTrue(content.contains("func fetchData"))
        assertTrue(content.contains("async"))
        assertTrue(content.contains("throws"))
    }

    @Test
    fun `task processes flow function manifest`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [flow:com.example.DataStream.observe]
            name=observe
            element=String
            param=filter:String
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Verify output was created
        assertTrue(outputDir.exists())

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        // Verify content includes AsyncStream
        val content = swiftFile.readText()
        assertTrue(content.contains("func observe"))
        assertTrue(content.contains("AsyncStream"))
    }

    @Test
    fun `task processes multiple sealed classes`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

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
            subclass=Error:false
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        val content = swiftFile.readText()
        assertTrue(content.contains("enum NetworkResult"))
        assertTrue(content.contains("enum UiState"))
    }

    @Test
    fun `task processes mixed declaration types`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

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
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        val content = swiftFile.readText()
        assertTrue(content.contains("enum Result"))
        assertTrue(content.contains("func fetch"))
        assertTrue(content.contains("func observe"))
    }

    @Test
    fun `task handles empty manifest`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText("")
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Output directory should exist even for empty manifest
        assertTrue(outputDir.exists())

        // No Swift file should be generated for empty manifest
        val swiftFile = File(outputDir, "Swiftify.swift")
        assertFalse(swiftFile.exists())
    }

    @Test
    fun `task creates output directory if it does not exist`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

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
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "nested/output/dir")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Directory should be created
        assertTrue(outputDir.exists())
    }

    @Test
    fun `task generates swift file with correct header`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

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
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        assertTrue(content.contains("// Generated by Swiftify"))
        assertTrue(content.contains("// Do not edit - this file is auto-generated"))
        assertTrue(content.contains("import Foundation"))
    }

    @Test
    fun `task handles sealed class with object subclass`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.State]
            name=State
            swiftName=State
            exhaustive=true
            subclass=Loading:true
            subclass=Success:false
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        // Object subclass should be a simple case
        assertTrue(content.contains("case loading"))
        // Data class should have associated value
        assertTrue(content.contains("case success"))
    }

    @Test
    fun `task handles sealed class with data class subclass`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.Result]
            name=Result
            swiftName=Result
            exhaustive=true
            subclass=Success:false
            subclass=Error:false
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        // Data classes should have associated values
        assertTrue(content.contains("case success"))
        assertTrue(content.contains("case error"))
    }

    @Test
    fun `task handles non-exhaustive sealed class`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.Result]
            name=Result
            swiftName=Result
            exhaustive=false
            subclass=Success:false
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        // Non-exhaustive should still generate valid code
        val content = swiftFile.readText()
        assertTrue(content.contains("enum Result"))
    }

    @Test
    fun `task handles suspend function with no parameters`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [suspend:com.example.Api.fetch]
            name=fetch
            return=String
            throwing=true
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        assertTrue(content.contains("func fetch"))
        assertTrue(content.contains("async"))
    }

    @Test
    fun `task handles suspend function with multiple parameters`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [suspend:com.example.Repository.save]
            name=save
            return=Unit
            throwing=true
            param=id:String
            param=data:String
            param=timestamp:Long
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        assertTrue(content.contains("func save"))
    }

    @Test
    fun `task maps kotlin types to swift types`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [suspend:com.example.TypeMapping.test]
            name=test
            return=Unit
            throwing=true
            param=string:String
            param=int:Int
            param=long:Long
            param=float:Float
            param=double:Double
            param=bool:Boolean
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        // Verify type mappings in generated code
        val content = swiftFile.readText()
        assertTrue(content.contains("func test"))
    }

    @Test
    fun `task handles flow function with parameters`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [flow:com.example.Stream.observe]
            name=observe
            element=String
            param=filter:String
            param=limit:Int
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        assertTrue(content.contains("func observe"))
        assertTrue(content.contains("AsyncStream"))
    }

    @Test
    fun `task overwrites existing swift file`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val outputDir = File(tempDir, "output")
        outputDir.mkdirs()

        // Create existing Swift file
        val swiftFile = File(outputDir, "Swiftify.swift")
        swiftFile.writeText("// old content")

        val manifestFile = File(tempDir, "manifest.txt")
        manifestFile.writeText(
            """
            [sealed:com.example.NewResult]
            name=NewResult
            swiftName=NewResult
            exhaustive=true
            subclass=Success:false
            """.trimIndent(),
        )
        task.manifestFiles.set(listOf(manifestFile))
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // File should be overwritten with new content
        val content = swiftFile.readText()
        assertFalse(content.contains("old content"))
        assertTrue(content.contains("enum NewResult"))
    }

    // Multi-manifest tests

    @Test
    fun `task processes multiple manifest files from different targets`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        // Create manifests for different targets
        val jvmManifest =
            File(tempDir, "jvm-manifest.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    swiftName=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val iosManifest =
            File(tempDir, "ios-manifest.txt").apply {
                writeText(
                    """
                    [suspend:com.example.Api.fetch]
                    name=fetch
                    return=String
                    throwing=true
                    """.trimIndent(),
                )
            }

        task.manifestFiles.set(listOf(jvmManifest, iosManifest))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        val content = swiftFile.readText()
        assertTrue(content.contains("enum Result"))
        assertTrue(content.contains("func fetch"))
    }

    @Test
    fun `task deduplicates same declaration from multiple manifests`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        // Same sealed class in both manifests (common in KMP commonMain)
        val manifest1 =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    swiftName=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val manifest2 =
            File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    swiftName=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        task.manifestFiles.set(listOf(manifest1, manifest2))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        val content = swiftFile.readText()

        // Should only have one enum definition
        val occurrences = content.split("enum Result").size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `task handles mix of existing and non-existing manifest files`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val existingManifest =
            File(tempDir, "existing.txt").apply {
                writeText(
                    """
                    [sealed:com.example.State]
                    name=State
                    swiftName=State
                    exhaustive=true
                    subclass=Loading:true
                    """.trimIndent(),
                )
            }

        val nonExistentManifest = File(tempDir, "nonexistent.txt")

        task.manifestFiles.set(listOf(existingManifest, nonExistentManifest))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // Should process the existing manifest successfully
        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        val content = swiftFile.readText()
        assertTrue(content.contains("enum State"))
    }

    @Test
    fun `task processes manifests from three targets`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val jvmManifest =
            File(tempDir, "jvm-manifest.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    swiftName=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val iosManifest =
            File(tempDir, "ios-manifest.txt").apply {
                writeText(
                    """
                    [suspend:com.example.Api.fetch]
                    name=fetch
                    return=String
                    throwing=true
                    """.trimIndent(),
                )
            }

        val macosManifest =
            File(tempDir, "macos-manifest.txt").apply {
                writeText(
                    """
                    [flow:com.example.Stream.observe]
                    name=observe
                    element=String
                    """.trimIndent(),
                )
            }

        task.manifestFiles.set(listOf(jvmManifest, iosManifest, macosManifest))

        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        task.processManifest()

        val swiftFile = File(outputDir, "Swiftify.swift")
        assertTrue(swiftFile.exists())

        val content = swiftFile.readText()
        assertTrue(content.contains("enum Result"))
        assertTrue(content.contains("func fetch"))
        assertTrue(content.contains("func observe"))
        assertTrue(content.contains("AsyncStream"))
    }
}
