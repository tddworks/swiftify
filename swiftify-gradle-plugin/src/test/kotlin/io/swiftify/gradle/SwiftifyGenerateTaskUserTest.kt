package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * User-focused tests for SwiftifyGenerateTask.
 *
 * These tests express what a user expects when using Swiftify:
 * - "I have Kotlin code with annotations, I expect Swift code to be generated"
 * - "Functions with default parameters should have convenience overloads"
 * - "Sealed classes should become Swift enums"
 * - "Flow functions should become AsyncStream"
 */
@DisplayName("SwiftifyGenerateTask - User Expectations")
class SwiftifyGenerateTaskUserTest {
    private lateinit var project: Project
    private lateinit var outputDir: File
    private lateinit var sourceDir: File

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        project = ProjectBuilder
            .builder()
            .withProjectDir(tempDir)
            .withName("TestFramework")
            .build()
        project.plugins.apply("io.swiftify")

        outputDir = File(tempDir, "build/generated/swiftify")
        sourceDir = File(tempDir, "src/commonMain/kotlin/com/example")
        sourceDir.mkdirs()
    }

    @Nested
    @DisplayName("When I have no Kotlin files")
    inner class NoSourceFiles {
        @Test
        fun `swiftifyGenerate should run without errors`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            // Should not throw
            task.generate()
        }

        @Test
        fun `should not create Swift files`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val swiftFiles = outputDir.listFiles()?.filter { it.extension == "swift" } ?: emptyList()
            // Only runtime file should be created (or none)
            assertTrue(swiftFiles.size <= 1, "No generated Swift files expected (except possibly runtime)")
        }
    }

    @Nested
    @DisplayName("When I have a suspend function with default parameters")
    inner class SuspendFunctionWithDefaults {
        @BeforeEach
        fun createSourceFile() {
            File(sourceDir, "UserRepository.kt").writeText(
                """
                package com.example

                import io.swiftify.annotations.SwiftDefaults

                class UserRepository {
                    @SwiftDefaults
                    suspend fun getUsers(
                        page: Int = 1,
                        limit: Int = 20
                    ): List<String> = emptyList()
                }
                """.trimIndent(),
            )
        }

        @Test
        fun `should generate Swift file with convenience overloads`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            // Check that a Swift file was generated
            val generatedFiles = outputDir.listFiles()?.filter { it.extension == "swift" } ?: emptyList()
            assertTrue(generatedFiles.isNotEmpty(), "Expected at least one Swift file")
        }

        @Test
        fun `generated Swift should have no-argument convenience overload`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val swiftContent = findGeneratedSwiftContent()
            assertContains(
                swiftContent,
                "func getUsers()",
                message = "Expected convenience overload with no arguments",
            )
        }

        @Test
        fun `generated Swift should call full function with default values`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val swiftContent = findGeneratedSwiftContent()
            assertContains(
                swiftContent,
                "page: 1",
                message = "Expected default value for page",
            )
            assertContains(
                swiftContent,
                "limit: 20",
                message = "Expected default value for limit",
            )
        }

        private fun findGeneratedSwiftContent(): String {
            val files = outputDir.listFiles()?.filter { it.extension == "swift" && it.name != "SwiftifyRuntime.swift" }
                ?: emptyList()
            return files.joinToString("\n") { it.readText() }
        }
    }

    @Nested
    @DisplayName("When I have a Flow-returning function")
    inner class FlowFunction {
        @BeforeEach
        fun createSourceFile() {
            File(sourceDir, "MessageRepository.kt").writeText(
                """
                package com.example

                import io.swiftify.annotations.SwiftFlow
                import kotlinx.coroutines.flow.Flow
                import kotlinx.coroutines.flow.flowOf

                class MessageRepository {
                    @SwiftFlow
                    fun watchMessages(roomId: String): Flow<String> = flowOf("message")
                }
                """.trimIndent(),
            )
        }

        @Test
        fun `should generate AsyncStream wrapper`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val swiftContent = findGeneratedSwiftContent()
            assertContains(
                swiftContent,
                "AsyncStream",
                message = "Expected AsyncStream in generated code",
            )
        }

        @Test
        fun `generated wrapper should use SwiftifyFlowCollector`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val swiftContent = findGeneratedSwiftContent()
            assertContains(
                swiftContent,
                "SwiftifyFlowCollector",
                message = "Expected SwiftifyFlowCollector in generated code",
            )
        }

        private fun findGeneratedSwiftContent(): String {
            val files = outputDir.listFiles()?.filter { it.extension == "swift" && it.name != "SwiftifyRuntime.swift" }
                ?: emptyList()
            return files.joinToString("\n") { it.readText() }
        }
    }

    @Nested
    @DisplayName("When I configure framework name")
    inner class FrameworkNameConfiguration {
        @BeforeEach
        fun createSourceFile() {
            File(sourceDir, "Api.kt").writeText(
                """
                package com.example

                import io.swiftify.annotations.SwiftDefaults

                class Api {
                    @SwiftDefaults
                    suspend fun ping(): String = "pong"
                }
                """.trimIndent(),
            )
        }

        @Test
        fun `generated Swift should import the configured framework`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)
            task.frameworkName.set("MyCustomKit")

            task.generate()

            val swiftContent = findGeneratedSwiftContent()
            assertContains(
                swiftContent,
                "import MyCustomKit",
                message = "Expected import statement with custom framework name",
            )
        }

        private fun findGeneratedSwiftContent(): String {
            val files = outputDir.listFiles()?.filter { it.extension == "swift" } ?: emptyList()
            return files.joinToString("\n") { it.readText() }
        }
    }

    @Nested
    @DisplayName("Runtime support file")
    inner class RuntimeSupport {
        @BeforeEach
        fun createSourceFile() {
            File(sourceDir, "Repo.kt").writeText(
                """
                package com.example

                import io.swiftify.annotations.SwiftFlow
                import kotlinx.coroutines.flow.Flow
                import kotlinx.coroutines.flow.flowOf

                class Repo {
                    @SwiftFlow
                    fun watch(): Flow<String> = flowOf("x")
                }
                """.trimIndent(),
            )
        }

        @Test
        fun `should generate SwiftifyRuntime file`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val runtimeFile = File(outputDir, "SwiftifyRuntime.swift")
            assertTrue(runtimeFile.exists(), "Expected SwiftifyRuntime.swift to be generated")
        }

        @Test
        fun `runtime file should contain SwiftifyFlowCollector`() {
            val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
            task.outputDirectory.set(outputDir)

            task.generate()

            val runtimeContent = File(outputDir, "SwiftifyRuntime.swift").readText()
            assertContains(
                runtimeContent,
                "SwiftifyFlowCollector",
                message = "Runtime file should define SwiftifyFlowCollector",
            )
        }
    }
}
