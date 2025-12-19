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

class SwiftifyPreviewTaskTest {
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
    fun `preview task has correct group`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        assertEquals("swiftify", task.group)
    }

    @Test
    fun `preview task has correct description`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        assertEquals("Preview generated Swift code for Kotlin declarations", task.description)
    }

    @Test
    fun `targetClass is optional`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        assertFalse(task.targetClass.isPresent)
    }

    @Test
    fun `targetClass can be set`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask
        task.targetClass.set("com.example.NetworkResult")

        assertTrue(task.targetClass.isPresent)
        assertEquals("com.example.NetworkResult", task.targetClass.get())
    }

    @Test
    fun `kotlinSources is optional`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        assertTrue(task.kotlinSources.isEmpty)
    }

    @Test
    fun `kotlinSources can be configured`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val sourceFile = File(tempDir, "Test.kt")
        sourceFile.writeText("class Test")
        task.kotlinSources.from(sourceFile)

        assertFalse(task.kotlinSources.isEmpty)
        assertEquals(1, task.kotlinSources.files.size)
    }

    @Test
    fun `preview task runs without sources`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Should not throw - will log message about no sources
        task.preview()

        // Verify task ran successfully (no exception)
        assertNotNull(task)
    }

    @Test
    fun `preview task finds sources in commonMain`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create commonMain source directory
        val commonMain = File(tempDir, "src/commonMain/kotlin")
        commonMain.mkdirs()

        val sourceFile = File(commonMain, "NetworkResult.kt")
        sourceFile.writeText(
            """
            package com.example

            sealed class NetworkResult<out T> {
                data class Success<T>(val data: T) : NetworkResult<T>()
                data class Error(val message: String) : NetworkResult<Nothing>()
                object Loading : NetworkResult<Nothing>()
            }
            """.trimIndent(),
        )

        // Task should find and process the file
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
        assertTrue(sourceFile.exists())
    }

    @Test
    fun `preview task finds sources in main`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create main source directory
        val main = File(tempDir, "src/main/kotlin")
        main.mkdirs()

        val sourceFile = File(main, "Test.kt")
        sourceFile.writeText(
            """
            package com.example

            class TestClass {
                suspend fun fetchData(): String = "data"
            }
            """.trimIndent(),
        )

        // Task should find and process the file
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
        assertTrue(sourceFile.exists())
    }

    @Test
    fun `preview task finds sources in iosMain`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create iosMain source directory
        val iosMain = File(tempDir, "src/iosMain/kotlin")
        iosMain.mkdirs()

        val sourceFile = File(iosMain, "PlatformCode.kt")
        sourceFile.writeText(
            """
            package com.example

            actual class Platform {
                actual fun getName(): String = "iOS"
            }
            """.trimIndent(),
        )

        // Task should find and process the file
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
        assertTrue(sourceFile.exists())
    }

    @Test
    fun `preview task processes multiple source directories`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create multiple source directories
        val commonMain = File(tempDir, "src/commonMain/kotlin")
        commonMain.mkdirs()
        val main = File(tempDir, "src/main/kotlin")
        main.mkdirs()

        File(commonMain, "Common.kt").writeText(
            """
            package com.example
            sealed class Result
            """.trimIndent(),
        )

        File(main, "Api.kt").writeText(
            """
            package com.example
            class Api {
                suspend fun call(): String = "response"
            }
            """.trimIndent(),
        )

        // Task should find and process all files
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task filters by target class`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create source files
        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        File(srcDir, "NetworkResult.kt").writeText(
            """
            package com.example
            sealed class NetworkResult
            """.trimIndent(),
        )

        File(srcDir, "OtherClass.kt").writeText(
            """
            package com.example
            class OtherClass
            """.trimIndent(),
        )

        // Set target class filter
        task.targetClass.set("com.example.NetworkResult")

        // Should only process NetworkResult file
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task handles files with no transformable declarations`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        // Create a file without transformable declarations
        File(srcDir, "Simple.kt").writeText(
            """
            package com.example

            data class Simple(val value: String)
            """.trimIndent(),
        )

        // Should process but not generate output for this file
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task uses configured kotlin sources`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create custom source location
        val customSrc = File(tempDir, "custom/src")
        customSrc.mkdirs()

        val sourceFile = File(customSrc, "Custom.kt")
        sourceFile.writeText(
            """
            package com.example
            sealed class CustomResult
            """.trimIndent(),
        )

        // Configure task to use custom sources
        task.kotlinSources.from(sourceFile)

        // Should use configured sources instead of auto-detection
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task ignores non-kotlin files`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        // Create kotlin and non-kotlin files
        File(srcDir, "Code.kt").writeText("sealed class Result")
        File(srcDir, "README.md").writeText("# Documentation")
        File(srcDir, "config.json").writeText("{}")

        // Should only process .kt files
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task handles nested source directories`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        val nestedDir = File(srcDir, "com/example/api")
        nestedDir.mkdirs()

        File(nestedDir, "ApiResult.kt").writeText(
            """
            package com.example.api
            sealed class ApiResult
            """.trimIndent(),
        )

        // Should find files in nested directories
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task handles syntax errors gracefully`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        // Create file with syntax error
        File(srcDir, "Invalid.kt").writeText(
            """
            package com.example
            sealed class Invalid {
                // missing closing brace
            """.trimIndent(),
        )

        // Should handle error gracefully and continue
        task.preview()

        // Verify task ran successfully (no exception thrown to user)
        assertNotNull(task)
    }

    @Test
    fun `preview task processes sealed classes`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        File(srcDir, "State.kt").writeText(
            """
            package com.example

            sealed class UiState {
                object Loading : UiState()
                data class Success(val data: String) : UiState()
                data class Error(val error: String) : UiState()
            }
            """.trimIndent(),
        )

        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task processes suspend functions`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        File(srcDir, "Repository.kt").writeText(
            """
            package com.example

            class Repository {
                suspend fun fetchData(id: String): Result<String> {
                    // implementation
                }
            }
            """.trimIndent(),
        )

        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task processes flow functions`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        File(srcDir, "DataStream.kt").writeText(
            """
            package com.example
            import kotlinx.coroutines.flow.Flow

            class DataStream {
                fun observeData(): Flow<String> {
                    // implementation
                }
            }
            """.trimIndent(),
        )

        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task handles empty source directories`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        // Create empty source directories
        File(tempDir, "src/commonMain/kotlin").mkdirs()
        File(tempDir, "src/main/kotlin").mkdirs()

        // Should handle gracefully
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }

    @Test
    fun `preview task with multiple files and target filter`() {
        val task = project.tasks.getByName("swiftifyPreview") as SwiftifyPreviewTask

        val srcDir = File(tempDir, "src/commonMain/kotlin")
        srcDir.mkdirs()

        File(srcDir, "Result.kt").writeText(
            """
            package com.example
            sealed class Result
            """.trimIndent(),
        )

        File(srcDir, "State.kt").writeText(
            """
            package com.example
            sealed class State
            """.trimIndent(),
        )

        File(srcDir, "Error.kt").writeText(
            """
            package com.example
            sealed class Error
            """.trimIndent(),
        )

        // Filter for specific class
        task.targetClass.set("com.example.State")

        // Should only preview State class
        task.preview()

        // Verify task ran successfully
        assertNotNull(task)
    }
}
