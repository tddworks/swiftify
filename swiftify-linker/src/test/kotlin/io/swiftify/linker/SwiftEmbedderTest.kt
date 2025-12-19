package io.swiftify.linker

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SwiftEmbedderTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var analyzer: FrameworkAnalyzer
    private lateinit var compiler: SwiftCompiler
    private lateinit var merger: BinaryMerger
    private lateinit var embedder: SwiftEmbedder

    @BeforeEach
    fun setup() {
        analyzer = mockk()
        compiler = mockk()
        merger = mockk()
        embedder = SwiftEmbedder(analyzer, compiler, merger)
    }

    @Test
    fun `embed orchestrates full flow successfully`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")
        val objectFile = File(tempDir, "Swiftify.o")
        objectFile.writeText("mock")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.MACOS,
            targetTriple = "arm64-apple-macos"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Success(listOf(objectFile))
        every { merger.merge(any(), any()) } returns MergeResult.Success(frameworkInfo.binaryPath)

        val result = embedder.embed(framework, listOf(swiftFile))

        assertIs<EmbedResult.Success>(result)
        verify { analyzer.analyze(framework) }
        verify { compiler.compile(listOf(swiftFile), any()) }
        verify { merger.merge(listOf(objectFile), any()) }
    }

    @Test
    fun `embed fails if framework analysis fails`() {
        val framework = File(tempDir, "Invalid.framework")
        val swiftFile = createSwiftFile("Swiftify.swift")

        every { analyzer.analyze(framework) } throws FrameworkAnalysisException("Invalid")

        val result = embedder.embed(framework, listOf(swiftFile))

        assertIs<EmbedResult.Error>(result)
        assertTrue(result.message.contains("Invalid"))
    }

    @Test
    fun `embed fails if compilation fails`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.MACOS,
            targetTriple = "arm64-apple-macos"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Error("Compilation failed")

        val result = embedder.embed(framework, listOf(swiftFile))

        assertIs<EmbedResult.Error>(result)
        assertTrue(result.message.contains("Compilation failed"))
    }

    @Test
    fun `embed fails if merge fails`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")
        val objectFile = File(tempDir, "Swiftify.o")
        objectFile.writeText("mock")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.MACOS,
            targetTriple = "arm64-apple-macos"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Success(listOf(objectFile))
        every { merger.merge(any(), any()) } returns MergeResult.Error("Merge failed")

        val result = embedder.embed(framework, listOf(swiftFile))

        assertIs<EmbedResult.Error>(result)
        assertTrue(result.message.contains("Merge failed"))
    }

    @Test
    fun `embed passes correct target triple to compiler`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")
        val objectFile = File(tempDir, "Swiftify.o")
        objectFile.writeText("mock")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.IOS,
            targetTriple = "arm64-apple-ios"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Success(listOf(objectFile))
        every { merger.merge(any(), any()) } returns MergeResult.Success(frameworkInfo.binaryPath)

        embedder.embed(framework, listOf(swiftFile))

        verify {
            compiler.compile(any(), match { config ->
                config.targetTriple == "arm64-apple-ios"
            })
        }
    }

    @Test
    fun `embed passes framework path to compiler config`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")
        val objectFile = File(tempDir, "Swiftify.o")
        objectFile.writeText("mock")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.MACOS,
            targetTriple = "arm64-apple-macos"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Success(listOf(objectFile))
        every { merger.merge(any(), any()) } returns MergeResult.Success(frameworkInfo.binaryPath)

        embedder.embed(framework, listOf(swiftFile))

        verify {
            compiler.compile(any(), match { config ->
                config.frameworkPath == framework
            })
        }
    }

    @Test
    fun `embed returns error for empty swift files list`() {
        val framework = createMockFramework("TestKit")

        val result = embedder.embed(framework, emptyList())

        assertIs<EmbedResult.Error>(result)
    }

    @Test
    fun `embed with config respects dry run mode`() {
        val framework = createMockFramework("TestKit")
        val swiftFile = createSwiftFile("Swiftify.swift")
        val objectFile = File(tempDir, "Swiftify.o")
        objectFile.writeText("mock")

        val frameworkInfo = FrameworkInfo(
            name = "TestKit",
            binaryPath = File(framework, "TestKit"),
            platform = Platform.MACOS,
            targetTriple = "arm64-apple-macos"
        )

        every { analyzer.analyze(framework) } returns frameworkInfo
        every { compiler.compile(any(), any()) } returns CompileResult.Success(listOf(objectFile))
        every { merger.merge(any(), any()) } returns MergeResult.Success(frameworkInfo.binaryPath)

        val config = EmbedConfig(dryRun = true)
        embedder.embed(framework, listOf(swiftFile), config)

        verify {
            compiler.compile(any(), match { it.dryRun })
            merger.merge(any(), match { it.dryRun })
        }
    }

    private fun createMockFramework(name: String): File {
        val framework = File(tempDir, "$name.framework")
        framework.mkdirs()
        val binary = File(framework, name)
        binary.writeBytes(ByteArray(100))
        return framework
    }

    private fun createSwiftFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("import Foundation")
        return file
    }
}
