package io.swiftify.linker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwiftCompilerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `builds compile command with correct flags`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
                sdkPath = "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk",
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        assertTrue(command.contains("swiftc"))
        assertTrue(command.contains("-target"))
        assertTrue(command.contains("arm64-apple-macos14.0"))
        assertTrue(command.contains("-sdk"))
        assertTrue(command.contains("-emit-object"))
    }

    @Test
    fun `includes framework search path in command`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val frameworkPath = File(tempDir, "MyKit.framework")
        frameworkPath.mkdirs()
        val config =
            CompileConfig(
                frameworkPath = frameworkPath,
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        assertTrue(command.contains("-F"))
        assertTrue(command.any { it == frameworkPath.parentFile.absolutePath })
    }

    @Test
    fun `imports underlying module for Swift overlay`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val frameworkPath = File(tempDir, "SampleKit.framework")
        frameworkPath.mkdirs()
        val config =
            CompileConfig(
                frameworkPath = frameworkPath,
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        // Uses -import-underlying-module instead of -framework for Swift overlay pattern
        assertTrue(command.contains("-import-underlying-module"))
    }

    @Test
    fun `outputs object file for each source`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Swiftify.swift", "import Foundation")
        val outputDir = File(tempDir, "output")
        outputDir.mkdirs()
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
                outputDirectory = outputDir,
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        assertTrue(command.contains("-o"))
        assertTrue(command.any { it.endsWith(".o") })
    }

    @Test
    fun `sets module name same as framework for Swift overlay`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        assertTrue(command.contains("-module-name"))
        // Module name matches framework name for Swift overlay pattern
        assertTrue(command.contains("MyKit"))
    }

    @Test
    fun `compile returns success result in dry run mode`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
                dryRun = true,
            )

        val result = compiler.compile(listOf(swiftFile), config)

        assertIs<CompileResult.Success>(result)
    }

    @Test
    fun `compile returns error for empty source list`() {
        val compiler = SwiftCompiler()
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
            )

        val result = compiler.compile(emptyList(), config)

        assertIs<CompileResult.Error>(result)
    }

    @Test
    fun `compile returns error for non-existent source files`() {
        val compiler = SwiftCompiler()
        val nonExistent = File(tempDir, "NonExistent.swift")
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-macos14.0",
                dryRun = true,
            )

        val result = compiler.compile(listOf(nonExistent), config)

        assertIs<CompileResult.Error>(result)
    }

    @Test
    fun `uses iOS SDK for iOS target`() {
        val compiler = SwiftCompiler()
        val swiftFile = createSwiftFile("Test.swift", "import Foundation")
        val config =
            CompileConfig(
                frameworkPath = File(tempDir, "MyKit.framework"),
                targetTriple = "arm64-apple-ios14.0",
                sdkPath = "/path/to/iPhoneOS.sdk",
            )

        val command = compiler.buildCommand(listOf(swiftFile), config)

        assertTrue(command.contains("arm64-apple-ios14.0"))
        assertTrue(command.any { it.contains("iPhoneOS.sdk") })
    }

    private fun createSwiftFile(
        name: String,
        content: String,
    ): File {
        val file = File(tempDir, name)
        file.writeText(content)
        return file
    }
}
