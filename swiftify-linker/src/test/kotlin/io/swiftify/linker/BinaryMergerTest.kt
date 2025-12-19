package io.swiftify.linker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BinaryMergerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `builds merge command with ld`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFile = createObjectFile("Swiftify.o")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = merger.buildCommand(listOf(objectFile), config)

        assertTrue(command.contains("ld"))
        assertTrue(command.contains("-r")) // Relocatable output
        assertTrue(command.any { it == originalBinary.absolutePath })
        assertTrue(command.any { it == objectFile.absolutePath })
    }

    @Test
    fun `outputs to specified location`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFile = createObjectFile("Swiftify.o")
        val outputFile = File(tempDir, "output/MyKit")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
                outputBinary = outputFile,
            )

        val command = merger.buildCommand(listOf(objectFile), config)

        assertTrue(command.contains("-o"))
        assertTrue(command.any { it == outputFile.absolutePath })
    }

    @Test
    fun `includes architecture flag`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFile = createObjectFile("Swiftify.o")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = merger.buildCommand(listOf(objectFile), config)

        assertTrue(command.contains("-arch"))
        assertTrue(command.contains("arm64"))
    }

    @Test
    fun `handles x64 architecture`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFile = createObjectFile("Swiftify.o")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "x86_64-apple-macos14.0",
            )

        val command = merger.buildCommand(listOf(objectFile), config)

        assertTrue(command.contains("-arch"))
        assertTrue(command.contains("x86_64"))
    }

    @Test
    fun `merge returns success in dry run mode`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFile = createObjectFile("Swiftify.o")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
                dryRun = true,
            )

        val result = merger.merge(listOf(objectFile), config)

        assertIs<MergeResult.Success>(result)
    }

    @Test
    fun `merge returns error for empty object list`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
            )

        val result = merger.merge(emptyList(), config)

        assertIs<MergeResult.Error>(result)
    }

    @Test
    fun `merge returns error for missing original binary`() {
        val merger = BinaryMerger()
        val nonExistent = File(tempDir, "NonExistent")
        val objectFile = createObjectFile("Swiftify.o")
        val config =
            MergeConfig(
                originalBinary = nonExistent,
                targetTriple = "arm64-apple-macos14.0",
                dryRun = true,
            )

        val result = merger.merge(listOf(objectFile), config)

        assertIs<MergeResult.Error>(result)
    }

    @Test
    fun `merge returns error for missing object files`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val nonExistent = File(tempDir, "NonExistent.o")
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
                dryRun = true,
            )

        val result = merger.merge(listOf(nonExistent), config)

        assertIs<MergeResult.Error>(result)
    }

    @Test
    fun `handles multiple object files`() {
        val merger = BinaryMerger()
        val originalBinary = createMockBinary("MyKit")
        val objectFiles =
            listOf(
                createObjectFile("Swiftify1.o"),
                createObjectFile("Swiftify2.o"),
                createObjectFile("Swiftify3.o"),
            )
        val config =
            MergeConfig(
                originalBinary = originalBinary,
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = merger.buildCommand(objectFiles, config)

        objectFiles.forEach { objFile ->
            assertTrue(command.any { it == objFile.absolutePath })
        }
    }

    private fun createMockBinary(name: String): File {
        val binary = File(tempDir, name)
        binary.writeBytes(ByteArray(100)) // Mock binary content
        return binary
    }

    private fun createObjectFile(name: String): File {
        val objFile = File(tempDir, name)
        objFile.writeBytes(ByteArray(50)) // Mock object file content
        return objFile
    }
}
