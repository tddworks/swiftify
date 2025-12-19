package io.swiftify.linker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrameworkAnalyzerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `analyzes framework and returns info`() {
        // Given a mock framework structure
        val framework = createMockFramework("TestKit")
        val analyzer = FrameworkAnalyzer()

        // When analyzing
        val info = analyzer.analyze(framework)

        // Then returns framework info
        assertNotNull(info)
        assertEquals("TestKit", info.name)
    }

    @Test
    fun `detects framework name from directory`() {
        val framework = createMockFramework("MyFramework")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertEquals("MyFramework", info.name)
    }

    @Test
    fun `locates binary inside framework`() {
        val framework = createMockFramework("SampleKit")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertTrue(info.binaryPath.exists())
        assertEquals("SampleKit", info.binaryPath.name)
    }

    @Test
    fun `detects macOS platform from framework path`() {
        val framework = createMockFramework("TestKit", platform = "macos")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertEquals(Platform.MACOS, info.platform)
    }

    @Test
    fun `detects iOS platform from framework path`() {
        val framework = createMockFramework("TestKit", platform = "ios")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertEquals(Platform.IOS, info.platform)
    }

    @Test
    fun `builds target triple for macOS arm64`() {
        val framework = createMockFramework("TestKit", platform = "macos", arch = "arm64")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertTrue(info.targetTriple.contains("arm64"))
        assertTrue(info.targetTriple.contains("apple"))
        assertTrue(info.targetTriple.contains("macos"))
    }

    @Test
    fun `builds target triple for iOS arm64`() {
        val framework = createMockFramework("TestKit", platform = "ios", arch = "arm64")
        val analyzer = FrameworkAnalyzer()

        val info = analyzer.analyze(framework)

        assertTrue(info.targetTriple.contains("arm64"))
        assertTrue(info.targetTriple.contains("apple"))
        assertTrue(info.targetTriple.contains("ios"))
    }

    @Test
    fun `returns error for non-existent framework`() {
        val nonExistent = File(tempDir, "NonExistent.framework")
        val analyzer = FrameworkAnalyzer()

        val result = analyzer.analyzeOrNull(nonExistent)

        assertEquals(null, result)
    }

    @Test
    fun `returns error for invalid framework structure`() {
        // Framework without binary
        val invalidFramework = File(tempDir, "Invalid.framework").apply { mkdirs() }
        val analyzer = FrameworkAnalyzer()

        val result = analyzer.analyzeOrNull(invalidFramework)

        assertEquals(null, result)
    }

    // Helper to create mock framework structure
    private fun createMockFramework(
        name: String,
        platform: String = "macos",
        arch: String = "arm64",
    ): File {
        val platformDir =
            when (platform) {
                "macos" -> "macosArm64"
                "ios" -> "iosArm64"
                else -> platform
            }

        val frameworkDir = File(tempDir, "bin/$platformDir/debugFramework/$name.framework")
        frameworkDir.mkdirs()

        // Create mock binary
        val binary = File(frameworkDir, name)
        binary.writeText("mock binary")

        // Create Info.plist (optional but realistic)
        File(frameworkDir, "Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>$name</string>
            </dict>
            </plist>
            """.trimIndent(),
        )

        return frameworkDir
    }
}
