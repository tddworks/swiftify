package io.swiftify.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertContains

/**
 * Tests for Swiftify exception classes and error handling.
 */
class SwiftifyExceptionTest {

    @Test
    fun `SwiftifyException has correct message`() {
        val exception = SwiftifyException("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun `SwiftifyException preserves cause`() {
        val cause = RuntimeException("Root cause")
        val exception = SwiftifyException("Test error", cause)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SwiftifyAnalysisException includes source file info`() {
        val exception = SwiftifyAnalysisException(
            "Parse error",
            sourceFile = "MyClass.kt",
            lineNumber = 42
        )
        assertContains(exception.message!!, "MyClass.kt:42")
    }

    @Test
    fun `SwiftifyGenerationException includes spec info`() {
        val exception = SwiftifyGenerationException(
            "Generation failed",
            specType = "enum",
            specName = "NetworkResult"
        )
        assertContains(exception.message!!, "enum")
        assertContains(exception.message!!, "NetworkResult")
    }

    @Test
    fun `SwiftifyConfigurationException includes config key`() {
        val exception = SwiftifyConfigurationException(
            "Invalid value",
            configKey = "swiftify.enabled"
        )
        assertContains(exception.message!!, "swiftify.enabled")
    }

    @Test
    fun `SwiftifyFrameworkException includes framework name`() {
        val exception = SwiftifyFrameworkException(
            "Framework not found",
            frameworkName = "MyApp"
        )
        assertContains(exception.message!!, "MyApp")
    }

    @Test
    fun `SwiftifyValidationException handles single error`() {
        val exception = SwiftifyValidationException("Single error")
        assertEquals("Single error", exception.message)
    }

    @Test
    fun `SwiftifyValidationException handles multiple errors`() {
        val errors = listOf(
            SwiftifyValidationException.ValidationError("Error 1"),
            SwiftifyValidationException.ValidationError("Error 2")
        )
        val exception = SwiftifyValidationException(errors)
        assertContains(exception.message!!, "Error 1")
        assertContains(exception.message!!, "Error 2")
        assertContains(exception.message!!, "Multiple validation errors")
    }

    @Test
    fun `ValidationError includes field and value info`() {
        val error = SwiftifyValidationException.ValidationError(
            message = "Invalid name",
            field = "name",
            value = ""
        )
        assertEquals("Invalid name", error.message)
        assertEquals("name", error.field)
        assertEquals("", error.value)
    }
}
