package io.swiftify.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwiftifyExtensionTest {
    private lateinit var project: Project
    private lateinit var extension: SwiftifyExtension

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
        extension = project.extensions.getByType(SwiftifyExtension::class.java)
    }

    @Test
    fun `enabled is true by default`() {
        assertTrue(extension.enabled.get())
    }

    @Test
    fun `enabled can be disabled`() {
        extension.enabled.set(false)
        assertFalse(extension.enabled.get())
    }

    @Test
    fun `frameworkName defaults to capitalized project name`() {
        assertEquals("TestProject", extension.frameworkName.get())
    }

    @Test
    fun `frameworkName can be set via function`() {
        extension.frameworkName("CustomKit")

        assertEquals("CustomKit", extension.frameworkName.get())
        assertTrue(extension.frameworkNameExplicitlySet)
    }

    @Test
    fun `frameworkName set via property does not mark as explicitly set`() {
        extension.frameworkName.set("CustomKit")

        assertEquals("CustomKit", extension.frameworkName.get())
        assertFalse(extension.frameworkNameExplicitlySet)
    }

    @Test
    fun `sealedClasses configuration works`() {
        extension.sealedClasses {
            transformToEnum(exhaustive = false)
            conformTo("Codable", "Equatable")
        }

        assertFalse(extension.sealedClassConfig.exhaustive)
        assertEquals(listOf("Codable", "Equatable"), extension.sealedClassConfig.conformances)
    }

    @Test
    fun `defaultParameters configuration works`() {
        extension.defaultParameters {
            generateOverloads(maxOverloads = 3)
        }

        assertEquals(3, extension.defaultParameterConfig.maxOverloads)
    }

    @Test
    fun `flowTypes configuration works`() {
        extension.flowTypes {
            transformToAsyncStream()
        }

        assertTrue(extension.flowConfig.useAsyncStream)
    }

    @Test
    fun `sealedClassConfig has correct defaults`() {
        assertTrue(extension.sealedClassConfig.exhaustive)
        assertTrue(extension.sealedClassConfig.conformances.isEmpty())
    }

    @Test
    fun `defaultParameterConfig has correct defaults`() {
        assertEquals(5, extension.defaultParameterConfig.maxOverloads)
    }

    @Test
    fun `flowConfig has correct defaults`() {
        assertTrue(extension.flowConfig.useAsyncStream)
    }
}
