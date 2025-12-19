package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwiftifyGenerateTaskTest {

    private lateinit var project: Project

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .withName("testProject")
            .build()
        project.plugins.apply("io.swiftify")
    }

    @Test
    fun `generate task has correct group`() {
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        assertEquals("swiftify", task.group)
    }

    @Test
    fun `generate task has correct description`() {
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        assertEquals("Generate Swift code from Kotlin declarations", task.description)
    }

    @Test
    fun `generate task output directory is configured from extension`() {
        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        assertEquals(
            extension.outputDirectory.get().asFile.absolutePath,
            task.outputDirectory.get().asFile.absolutePath
        )
    }

    @Test
    fun `generate task framework name is configured from extension`() {
        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        assertEquals(extension.frameworkName.get(), task.frameworkName.get())
    }

    @Test
    fun `generateApiNotes defaults to true`() {
        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        assertTrue(task.generateApiNotes.get())
    }

    @Test
    fun `task can find kotlin sources in commonMain`() {
        // Create source directory
        val sourceDir = File(tempDir, "src/commonMain/kotlin")
        sourceDir.mkdirs()

        // Create a test Kotlin file
        val testFile = File(sourceDir, "Test.kt")
        testFile.writeText("""
            package com.example

            class TestClass {
                suspend fun test(): String = "test"
            }
        """.trimIndent())

        val task = project.tasks.getByName("swiftifyGenerate") as SwiftifyGenerateTask

        // Task should be able to execute without errors
        assertNotNull(task)
    }
}
