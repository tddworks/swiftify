package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SwiftifyGenerateTaskTest {
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

    @Test
    fun `generate runs without errors when no source files exist`() {
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()
    }

    @Test
    fun `generate creates no Swift files when no source files exist`() {
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftFiles = outputDir.listFiles()?.filter { it.extension == "swift" } ?: emptyList()
        assertTrue(swiftFiles.size <= 1, "No generated Swift files expected (except possibly runtime)")
    }

    @Test
    fun `generate creates Swift file for suspend function with SwiftDefaults`() {
        createSourceFile("UserRepository.kt", """
            package com.example

            import io.swiftify.annotations.SwiftDefaults

            class UserRepository {
                @SwiftDefaults
                suspend fun getUsers(
                    page: Int = 1,
                    limit: Int = 20
                ): List<String> = emptyList()
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val generatedFiles = outputDir.listFiles()?.filter { it.extension == "swift" } ?: emptyList()
        assertTrue(generatedFiles.isNotEmpty(), "Expected at least one Swift file")
    }

    @Test
    fun `generate creates convenience overload with no arguments for SwiftDefaults function`() {
        createSourceFile("UserRepository.kt", """
            package com.example

            import io.swiftify.annotations.SwiftDefaults

            class UserRepository {
                @SwiftDefaults
                suspend fun getUsers(
                    page: Int = 1,
                    limit: Int = 20
                ): List<String> = emptyList()
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftContent = findGeneratedSwiftContent()
        assertContains(swiftContent, "func getUsers()", message = "Expected convenience overload with no arguments")
    }

    @Test
    fun `generate includes default values in convenience overload`() {
        createSourceFile("UserRepository.kt", """
            package com.example

            import io.swiftify.annotations.SwiftDefaults

            class UserRepository {
                @SwiftDefaults
                suspend fun getUsers(
                    page: Int = 1,
                    limit: Int = 20
                ): List<String> = emptyList()
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftContent = findGeneratedSwiftContent()
        assertContains(swiftContent, "page: 1", message = "Expected default value for page")
        assertContains(swiftContent, "limit: 20", message = "Expected default value for limit")
    }

    @Test
    fun `generate creates convenience overload for non-suspend function with SwiftDefaults`() {
        createSourceFile("Calculator.kt", """
            package com.example

            import io.swiftify.annotations.SwiftDefaults

            class Calculator {
                @SwiftDefaults
                fun calculate(
                    value: Int,
                    multiplier: Int = 2
                ): Int = value * multiplier
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftContent = findGeneratedSwiftContent()
        assertContains(swiftContent, "func calculate(value:", message = "Expected convenience overload for non-suspend function")
        assertContains(swiftContent, "multiplier: 2", message = "Expected default value for multiplier")
    }

    @Test
    fun `generate creates AsyncStream wrapper for SwiftFlow function`() {
        createSourceFile("MessageRepository.kt", """
            package com.example

            import io.swiftify.annotations.SwiftFlow
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flowOf

            class MessageRepository {
                @SwiftFlow
                fun watchMessages(roomId: String): Flow<String> = flowOf("message")
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftContent = findGeneratedSwiftContent()
        assertContains(swiftContent, "AsyncStream", message = "Expected AsyncStream in generated code")
    }

    @Test
    fun `generate uses SwiftifyFlowCollector for SwiftFlow function`() {
        createSourceFile("MessageRepository.kt", """
            package com.example

            import io.swiftify.annotations.SwiftFlow
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flowOf

            class MessageRepository {
                @SwiftFlow
                fun watchMessages(roomId: String): Flow<String> = flowOf("message")
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val swiftContent = findGeneratedSwiftContent()
        assertContains(swiftContent, "SwiftifyFlowCollector", message = "Expected SwiftifyFlowCollector in generated code")
    }

    @Test
    fun `generate imports configured framework name`() {
        createSourceFile("Api.kt", """
            package com.example

            import io.swiftify.annotations.SwiftDefaults

            class Api {
                @SwiftDefaults
                suspend fun ping(): String = "pong"
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)
        task.frameworkName.set("MyCustomKit")

        task.generate()

        val swiftContent = findGeneratedSwiftContent(includeRuntime = true)
        assertContains(swiftContent, "import MyCustomKit", message = "Expected import statement with custom framework name")
    }

    @Test
    fun `generate creates SwiftifyRuntime file for SwiftFlow function`() {
        createSourceFile("Repo.kt", """
            package com.example

            import io.swiftify.annotations.SwiftFlow
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flowOf

            class Repo {
                @SwiftFlow
                fun watch(): Flow<String> = flowOf("x")
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val runtimeFile = File(outputDir, "SwiftifyRuntime.swift")
        assertTrue(runtimeFile.exists(), "Expected SwiftifyRuntime.swift to be generated")
    }

    @Test
    fun `SwiftifyRuntime contains SwiftifyFlowCollector`() {
        createSourceFile("Repo.kt", """
            package com.example

            import io.swiftify.annotations.SwiftFlow
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flowOf

            class Repo {
                @SwiftFlow
                fun watch(): Flow<String> = flowOf("x")
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask
        task.outputDirectory.set(outputDir)

        task.generate()

        val runtimeContent = File(outputDir, "SwiftifyRuntime.swift").readText()
        assertContains(runtimeContent, "SwiftifyFlowCollector", message = "Runtime file should define SwiftifyFlowCollector")
    }

    private fun createSourceFile(name: String, content: String) {
        File(sourceDir, name).writeText(content)
    }

    private fun findGeneratedSwiftContent(includeRuntime: Boolean = false): String {
        val files = outputDir.listFiles()?.filter {
            it.extension == "swift" && (includeRuntime || it.name != "SwiftifyRuntime.swift")
        } ?: emptyList()
        return files.joinToString("\n") { it.readText() }
    }
}
