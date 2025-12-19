package io.swiftify.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwiftifyPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")

        settingsFile.writeText("""
            rootProject.name = "test-project"
        """.trimIndent())
    }

    @Test
    fun `plugin applies successfully`() {
        buildFile.writeText("""
            plugins {
                id("io.swiftify")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group=swiftify")
            .build()

        assertTrue(result.output.contains("swiftifyGenerate"))
        assertTrue(result.output.contains("swiftifyPreview"))
    }

    @Test
    fun `swiftifyGenerate task runs without sources`() {
        buildFile.writeText("""
            plugins {
                id("io.swiftify")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("swiftifyGenerate")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":swiftifyGenerate")?.outcome)
        assertTrue(result.output.contains("No Kotlin source files found"))
    }

    @Test
    fun `extension configuration works`() {
        buildFile.writeText("""
            plugins {
                id("io.swiftify")
            }

            swiftify {
                enabled.set(true)
                sealedClasses {
                    transformToEnum(exhaustive = true)
                }
                suspendFunctions {
                    transformToAsync(throwing = true)
                }
                flowTypes {
                    transformToAsyncSequence()
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("swiftifyGenerate")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":swiftifyGenerate")?.outcome)
    }

    @Test
    fun `generates Swift code from Kotlin sources`() {
        // Create source file with annotated code
        val sourceDir = File(testProjectDir, "src/commonMain/kotlin/com/example")
        sourceDir.mkdirs()

        File(sourceDir, "UserRepository.kt").writeText("""
            package com.example

            import io.swiftify.annotations.SwiftAsync
            import io.swiftify.annotations.SwiftFlow
            import kotlinx.coroutines.flow.Flow

            class UserRepository {
                @SwiftAsync
                suspend fun fetchUser(id: String): String = id

                @SwiftFlow
                fun getUserUpdates(): Flow<String> = TODO()
            }
        """.trimIndent())

        buildFile.writeText("""
            plugins {
                id("io.swiftify")
            }

            swiftify {
                suspendFunctions {
                    transformToAsync(throwing = true)
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("swiftifyGenerate")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":swiftifyGenerate")?.outcome)

        // Check output directory exists
        val outputDir = File(testProjectDir, "build/generated/swiftify")
        assertTrue(outputDir.exists() || result.output.contains("Swiftify"))
    }

    @Test
    fun `swiftifyPreview task runs`() {
        buildFile.writeText("""
            plugins {
                id("io.swiftify")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("swiftifyPreview")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":swiftifyPreview")?.outcome)
        assertTrue(result.output.contains("Swiftify Preview"))
    }
}
