package io.swiftify.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift runtime support code generation.
 *
 * SwiftRuntimeSupport generates helper classes needed for Kotlin/Swift bridging,
 * including SwiftifyFlowCollector for Flow → AsyncStream conversion.
 */
class SwiftRuntimeSupportTest {

    // ============================================================
    // Basic Structure Tests
    // ============================================================

    @Test
    fun `generate returns valid Swift code`() {
        val result = SwiftRuntimeSupport.generate()

        assertTrue(result.isNotBlank())
        assertContains(result, "import Foundation")
    }

    @Test
    fun `generate includes framework import`() {
        val result = SwiftRuntimeSupport.generate("SharedKit")

        assertContains(result, "import SharedKit")
    }

    @Test
    fun `generate with custom framework name imports that framework`() {
        val result = SwiftRuntimeSupport.generate("MyCustomFramework")

        assertContains(result, "import MyCustomFramework")
    }

    @Test
    fun `generate uses default framework name when not specified`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "import SharedKit")
    }

    // ============================================================
    // SwiftifyFlowCollector Tests
    // ============================================================

    @Test
    fun `generate includes SwiftifyFlowCollector class`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "class SwiftifyFlowCollector<T>")
    }

    @Test
    fun `SwiftifyFlowCollector extends NSObject`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, ": NSObject")
    }

    @Test
    fun `SwiftifyFlowCollector conforms to FlowCollector protocol`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "Kotlinx_coroutines_coreFlowCollector")
    }

    @Test
    fun `SwiftifyFlowCollector has onEmit callback`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "onEmit: (T) -> Void")
        assertContains(result, "let onEmit:")
    }

    @Test
    fun `SwiftifyFlowCollector has onComplete callback`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "onComplete: () -> Void")
        assertContains(result, "let onComplete:")
    }

    @Test
    fun `SwiftifyFlowCollector has onError callback`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "onError: (Error) -> Void")
        assertContains(result, "let onError:")
    }

    @Test
    fun `SwiftifyFlowCollector has public initializer`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "public init(")
        assertContains(result, "@escaping (T) -> Void")
    }

    @Test
    fun `SwiftifyFlowCollector implements emit method`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "func emit(value: Any?, completionHandler:")
    }

    @Test
    fun `emit method casts value to generic type`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "as? T")
        assertContains(result, "onEmit(typedValue)")
    }

    @Test
    fun `emit method calls completion handler`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "completionHandler(nil)")
    }

    @Test
    fun `SwiftifyFlowCollector is public`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "public class SwiftifyFlowCollector")
    }

    // ============================================================
    // Optional Extension Tests
    // ============================================================

    @Test
    fun `generate includes Optional extension`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "extension Optional")
    }

    @Test
    fun `Optional extension has unwrap method`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "func unwrap(or error: Error")
        assertContains(result, "throws -> Wrapped")
    }

    @Test
    fun `unwrap uses default SwiftifyError nullResult`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "SwiftifyError.nullResult")
    }

    // ============================================================
    // SwiftifyError Tests
    // ============================================================

    @Test
    fun `generate includes SwiftifyError enum`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "enum SwiftifyError")
    }

    @Test
    fun `SwiftifyError conforms to Error protocol`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "SwiftifyError: Error")
    }

    @Test
    fun `SwiftifyError conforms to LocalizedError`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "LocalizedError")
    }

    @Test
    fun `SwiftifyError has nullResult case`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "case nullResult")
    }

    @Test
    fun `SwiftifyError has kotlinException case`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "case kotlinException(message: String)")
    }

    @Test
    fun `SwiftifyError implements errorDescription`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "var errorDescription: String?")
    }

    @Test
    fun `nullResult has descriptive error message`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "Unexpected null result from Kotlin")
    }

    @Test
    fun `kotlinException has descriptive error message`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "Kotlin exception:")
    }

    @Test
    fun `SwiftifyError is public`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "public enum SwiftifyError")
    }

    // ============================================================
    // MARK Comments Tests
    // ============================================================

    @Test
    fun `generate includes MARK comment for runtime support`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "// MARK: - Swiftify Runtime Support")
    }

    @Test
    fun `generate includes descriptive comments`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "Helper classes for Kotlin/Swift bridging")
    }

    @Test
    fun `SwiftifyFlowCollector has documentation comment`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "Collector for bridging Kotlin Flow to Swift AsyncStream")
    }

    @Test
    fun `emit method has documentation comment`() {
        val result = SwiftRuntimeSupport.generate()

        assertContains(result, "FlowCollector protocol implementation")
    }

    // ============================================================
    // FILENAME Constant Tests
    // ============================================================

    @Test
    fun `FILENAME constant has correct value`() {
        assertEquals("SwiftifyRuntime.swift", SwiftRuntimeSupport.FILENAME)
    }

    @Test
    fun `FILENAME is a Swift file`() {
        assertTrue(SwiftRuntimeSupport.FILENAME.endsWith(".swift"))
    }

    // ============================================================
    // Code Quality Tests
    // ============================================================

    @Test
    fun `generated code has proper indentation`() {
        val result = SwiftRuntimeSupport.generate()

        // Check that there are no leading spaces (trimMargin should handle this)
        val lines = result.lines()
        lines.forEach { line ->
            // Lines should not start with the pipe character
            assertTrue(!line.startsWith("|"), "Line should not start with |: $line")
        }
    }

    @Test
    fun `generated code does not have trimMargin artifacts`() {
        val result = SwiftRuntimeSupport.generate()

        assertTrue(!result.contains("|import"))
        assertTrue(!result.contains("|public"))
        assertTrue(!result.contains("|class"))
    }

    @Test
    fun `generated code compiles conceptually`() {
        val result = SwiftRuntimeSupport.generate()

        // Should have matching braces
        val openBraces = result.count { it == '{' }
        val closeBraces = result.count { it == '}' }
        assertEquals(openBraces, closeBraces, "Mismatched braces in generated code")
    }

    // ============================================================
    // Edge Case Tests
    // ============================================================

    @Test
    fun `generate handles framework name with underscores`() {
        val result = SwiftRuntimeSupport.generate("My_Framework")

        assertContains(result, "import My_Framework")
    }

    @Test
    fun `generate handles framework name with numbers`() {
        val result = SwiftRuntimeSupport.generate("Framework2")

        assertContains(result, "import Framework2")
    }

    @Test
    fun `generate returns consistent output for same framework name`() {
        val result1 = SwiftRuntimeSupport.generate("TestFramework")
        val result2 = SwiftRuntimeSupport.generate("TestFramework")

        assertEquals(result1, result2)
    }
}
