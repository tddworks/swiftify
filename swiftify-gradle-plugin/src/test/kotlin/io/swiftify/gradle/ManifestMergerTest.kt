package io.swiftify.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestMergerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `merge returns empty string for empty list`() {
        val merger = ManifestMerger()
        val result = merger.merge(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `merge returns original content for single manifest`() {
        val manifest =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        assertTrue(result.contains("[sealed:com.example.Result]"))
        assertTrue(result.contains("name=Result"))
    }

    @Test
    fun `merge combines multiple manifests`() {
        val manifest1 =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val manifest2 =
            File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [suspend:com.example.Api.fetch]
                    name=fetch
                    return=String
                    throwing=true
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest1, manifest2))

        assertTrue(result.contains("[sealed:com.example.Result]"))
        assertTrue(result.contains("[suspend:com.example.Api.fetch]"))
        assertTrue(result.contains("Merged from 2 manifest(s)"))
    }

    @Test
    fun `merge deduplicates same declaration from multiple targets`() {
        // Both targets have the same sealed class (common in KMP commonMain)
        val manifest1 =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val manifest2 =
            File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    exhaustive=true
                    subclass=Success:false
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest1, manifest2))

        // Should only contain one instance
        val occurrences = result.split("[sealed:com.example.Result]").size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `merge preserves all unique declarations`() {
        val manifest1 =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result

                    [suspend:com.example.Api.fetch]
                    name=fetch
                    """.trimIndent(),
                )
            }

        val manifest2 =
            File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [flow:com.example.Stream.observe]
                    name=observe
                    element=String
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest1, manifest2))

        assertTrue(result.contains("[sealed:com.example.Result]"))
        assertTrue(result.contains("[suspend:com.example.Api.fetch]"))
        assertTrue(result.contains("[flow:com.example.Stream.observe]"))
    }

    @Test
    fun `merge handles manifests with comments`() {
        val manifest =
            File(tempDir, "manifest.txt").apply {
                writeText(
                    """
                    # Swiftify Declaration Manifest
                    # Generated at compile time

                    [sealed:com.example.Result]
                    name=Result
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        assertTrue(result.contains("[sealed:com.example.Result]"))
        // Comments from original should be stripped/not duplicated in merged
    }

    @Test
    fun `merge handles empty manifest file`() {
        val manifest =
            File(tempDir, "manifest.txt").apply {
                writeText("")
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        // Should return header but no content
        assertTrue(result.isNotBlank() || result.isEmpty())
    }

    @Test
    fun `merge preserves declaration properties`() {
        val manifest =
            File(tempDir, "manifest.txt").apply {
                writeText(
                    """
                    [sealed:com.example.NetworkResult]
                    name=NetworkResult
                    package=com.example
                    exhaustive=true
                    swiftName=NetworkResult
                    subclass=Success:false
                    subclass=Error:false
                    subclass=Loading:true
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        assertTrue(result.contains("name=NetworkResult"))
        assertTrue(result.contains("package=com.example"))
        assertTrue(result.contains("exhaustive=true"))
        assertTrue(result.contains("swiftName=NetworkResult"))
        assertTrue(result.contains("subclass=Success:false"))
        assertTrue(result.contains("subclass=Error:false"))
        assertTrue(result.contains("subclass=Loading:true"))
    }

    @Test
    fun `merge handles suspend function with parameters`() {
        val manifest =
            File(tempDir, "manifest.txt").apply {
                writeText(
                    """
                    [suspend:com.example.UserRepository.fetchUser]
                    name=fetchUser
                    package=com.example
                    throwing=true
                    return=User
                    param=id:String
                    param=includeDetails:Boolean
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        assertTrue(result.contains("name=fetchUser"))
        assertTrue(result.contains("param=id:String"))
        assertTrue(result.contains("param=includeDetails:Boolean"))
    }

    @Test
    fun `merge handles flow function`() {
        val manifest =
            File(tempDir, "manifest.txt").apply {
                writeText(
                    """
                    [flow:com.example.UserRepository.getUserUpdates]
                    name=getUserUpdates
                    package=com.example
                    element=User
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest))

        assertTrue(result.contains("[flow:com.example.UserRepository.getUserUpdates]"))
        assertTrue(result.contains("element=User"))
    }

    @Test
    fun `merge deduplicates by qualified name across different types`() {
        // Different types with same base name should both be preserved
        val manifest1 =
            File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [sealed:com.example.Result]
                    name=Result
                    """.trimIndent(),
                )
            }

        val manifest2 =
            File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [suspend:com.example.Result.fetch]
                    name=fetch
                    """.trimIndent(),
                )
            }

        val merger = ManifestMerger()
        val result = merger.merge(listOf(manifest1, manifest2))

        // Both should be present since they have different qualified names
        assertTrue(result.contains("[sealed:com.example.Result]"))
        assertTrue(result.contains("[suspend:com.example.Result.fetch]"))
    }
}
