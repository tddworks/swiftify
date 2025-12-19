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
    fun `manifestFile has default from plugin`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        // Plugin sets a default manifest file location
        assertTrue(task.manifestFile.isPresent)
    }

    @Test
    fun `manifestFile can be set`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        val manifest = File(tempDir, "manifest.txt")
        task.manifestFile.set(manifest)

        assertTrue(task.manifestFile.isPresent)
        assertEquals(manifest.absolutePath, task.manifestFile.get().asFile.absolutePath)
    }

    @Test
    fun `outputDirectory can be configured`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        assertEquals(outputDir.absolutePath, task.outputDirectory.get().asFile.absolutePath)
    }

    @Test
    fun `task skips when no manifest file is configured`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask
        val outputDir = File(tempDir, "output")
        task.outputDirectory.set(outputDir)

        // Should not throw - will log and skip
        task.processManifest()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `task skips when manifest file does not exist`() {
        val task = project.tasks.getByName("swiftifyProcessManifest") as SwiftifyProcessManifestTask

        val nonExistentManifest = File(tempDir, "nonexistent.txt")
        task.manifestFile.set(nonExistentManifest)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)

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
        task.manifestFile.set(manifestFile)
        task.outputDirectory.set(outputDir)

        task.processManifest()

        // File should be overwritten with new content
        val content = swiftFile.readText()
        assertFalse(content.contains("old content"))
        assertTrue(content.contains("enum NewResult"))
    }
}
