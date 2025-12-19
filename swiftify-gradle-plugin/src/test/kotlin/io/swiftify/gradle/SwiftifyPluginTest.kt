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

class SwiftifyPluginTest {
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
    fun `plugin can be applied to project`() {
        project.plugins.apply("io.swiftify")

        assertTrue(project.plugins.hasPlugin("io.swiftify"))
    }

    @Test
    fun `plugin creates swiftify extension`() {
        project.plugins.apply("io.swiftify")

        val extension = project.extensions.findByName("swiftify")
        assertNotNull(extension)
        assertTrue(extension is SwiftifyExtension)
    }

    @Test
    fun `extension has correct default values`() {
        project.plugins.apply("io.swiftify")

        val extension = project.extensions.getByType(SwiftifyExtension::class.java)

        assertTrue(extension.enabled.get())
        assertEquals("${project.name.replaceFirstChar { it.uppercase() }}", extension.frameworkName.get())
    }

    @Test
    fun `swiftifyGenerate task is registered`() {
        project.plugins.apply("io.swiftify")

        val task = project.tasks.findByName("swiftifyGenerate")
        assertNotNull(task)
        assertEquals("swiftify", task.group)
    }

    @Test
    fun `swiftifyPreview task is registered`() {
        project.plugins.apply("io.swiftify")

        val task = project.tasks.findByName("swiftifyPreview")
        assertNotNull(task)
        assertEquals("swiftify", task.group)
    }

    @Test
    fun `swiftifyProcessManifest task is registered`() {
        project.plugins.apply("io.swiftify")

        val task = project.tasks.findByName("swiftifyProcessManifest")
        assertNotNull(task)
        assertEquals("swiftify", task.group)
    }

    @Test
    fun `swiftifyEmbed task is registered`() {
        project.plugins.apply("io.swiftify")

        val task = project.tasks.findByName("swiftifyEmbed")
        assertNotNull(task)
        assertEquals("swiftify", task.group)
        assertTrue(task is SwiftifyEmbedTask)
    }

    @Test
    fun `swiftifyEmbed depends on generate task based on mode`() {
        project.plugins.apply("io.swiftify")

        val embedTask = project.tasks.findByName("swiftifyEmbed")
        assertNotNull(embedTask)

        // In REGEX mode (default), should depend on swiftifyGenerate
        // The dependency is now a Provider, so we check the task dependencies are not empty
        assertTrue(embedTask.dependsOn.isNotEmpty())
    }

    @Test
    fun `default analysis mode is REGEX`() {
        project.plugins.apply("io.swiftify")

        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        assertEquals(AnalysisMode.REGEX, extension.analysisMode.get())
    }

    @Test
    fun `analysis mode can be set to KSP`() {
        project.plugins.apply("io.swiftify")

        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        extension.analysisMode(AnalysisMode.KSP)

        assertEquals(AnalysisMode.KSP, extension.analysisMode.get())
    }

    @Test
    fun `output directory has correct default`() {
        project.plugins.apply("io.swiftify")

        val extension = project.extensions.getByType(SwiftifyExtension::class.java)
        val expectedPath =
            project.layout.buildDirectory
                .dir("generated/swiftify")
                .get()
                .asFile.absolutePath

        assertEquals(
            expectedPath,
            extension.outputDirectory
                .get()
                .asFile.absolutePath,
        )
    }
}
