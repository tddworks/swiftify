package io.swiftify.linker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertContains

/**
 * Tests for the Swiftify linker plugin.
 */
class LinkerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `linker returns error for non-existent framework`() {
        val config = SwiftifyLinkerConfig()
        val linker = SwiftifyLinkerPlugin(config)

        val result = linker.inject(
            frameworkDir = File(tempDir, "NonExistent.framework"),
            swiftSourceDir = tempDir
        )

        assertIs<InjectionResult.Error>(result)
        assertContains(result.message, "does not exist")
    }

    @Test
    fun `linker returns success when no Swift files present`() {
        val config = SwiftifyLinkerConfig()
        val linker = SwiftifyLinkerPlugin(config)

        val frameworkDir = File(tempDir, "Test.framework")
        frameworkDir.mkdirs()

        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()

        val result = linker.inject(frameworkDir, swiftDir)

        assertIs<InjectionResult.Success>(result)
        assertEquals(0, result.filesInjected)
    }

    @Test
    fun `linker copies Swift files to framework`() {
        val config = SwiftifyLinkerConfig(compileSwift = false)
        val linker = SwiftifyLinkerPlugin(config)

        val frameworkDir = File(tempDir, "Test.framework")
        frameworkDir.mkdirs()

        val swiftDir = File(tempDir, "swift")
        swiftDir.mkdirs()

        val swiftFile = File(swiftDir, "Swiftify.swift")
        swiftFile.writeText("public func test() {}")

        val result = linker.inject(frameworkDir, swiftDir)

        assertIs<InjectionResult.Success>(result)
        assertEquals(1, result.filesInjected)

        val copiedFile = File(frameworkDir, "Swiftify/Swiftify.swift")
        assertEquals(true, copiedFile.exists())
    }

    @Test
    fun `linker config defaults are correct`() {
        val config = SwiftifyLinkerConfig()

        assertEquals(false, config.compileSwift)
        assertEquals(null, config.targetTriple)
        assertEquals(null, config.sdkPath)
        assertEquals(false, config.dryRun)
    }
}
