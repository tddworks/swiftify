package io.swiftify.tests.unit

import io.swiftify.common.*
import io.swiftify.generator.SwiftEnumGenerator
import io.swiftify.generator.SwiftAsyncFunctionGenerator
import io.swiftify.generator.SwiftAsyncSequenceGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains

/**
 * Tests for generator validation and error handling.
 */
class GeneratorValidationTest {

    private val enumGenerator = SwiftEnumGenerator()
    private val asyncFunctionGenerator = SwiftAsyncFunctionGenerator()
    private val asyncSequenceGenerator = SwiftAsyncSequenceGenerator()

    // SwiftEnumGenerator validation tests

    @Test
    fun `enum generator throws for blank name`() {
        val spec = SwiftEnumSpec(
            name = "",
            cases = listOf(SwiftEnumCase("case1"))
        )
        val exception = assertThrows<SwiftifyValidationException> {
            enumGenerator.generate(spec)
        }
        assertContains(exception.message!!, "blank")
    }

    @Test
    fun `enum generator throws for empty cases`() {
        val spec = SwiftEnumSpec(
            name = "TestEnum",
            cases = emptyList()
        )
        val exception = assertThrows<SwiftifyValidationException> {
            enumGenerator.generate(spec)
        }
        assertContains(exception.message!!, "at least one case")
    }

    @Test
    fun `enum generator throws for blank case name`() {
        val spec = SwiftEnumSpec(
            name = "TestEnum",
            cases = listOf(SwiftEnumCase(""))
        )
        val exception = assertThrows<SwiftifyValidationException> {
            enumGenerator.generate(spec)
        }
        assertContains(exception.message!!, "Case name cannot be blank")
    }

    @Test
    fun `enum generator warns for lowercase name`() {
        val spec = SwiftEnumSpec(
            name = "testEnum",
            cases = listOf(SwiftEnumCase("case1"))
        )
        val exception = assertThrows<SwiftifyValidationException> {
            enumGenerator.generate(spec)
        }
        assertContains(exception.message!!, "uppercase")
    }

    // SwiftAsyncFunctionGenerator validation tests

    @Test
    fun `async function generator throws for blank name`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "",
            parameters = emptyList(),
            returnType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncFunctionGenerator.generate(spec)
        }
        assertContains(exception.message!!, "blank")
    }

    @Test
    fun `async function generator throws for blank parameter name`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "fetchData",
            parameters = listOf(SwiftParameter("", SwiftType.Named("Int"))),
            returnType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncFunctionGenerator.generate(spec)
        }
        assertContains(exception.message!!, "Parameter name cannot be blank")
    }

    @Test
    fun `async function generator succeeds with valid spec`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "fetchData",
            parameters = listOf(SwiftParameter("id", SwiftType.Named("Int"))),
            returnType = SwiftType.Named("String")
        )
        val result = asyncFunctionGenerator.generate(spec)
        assertContains(result, "func fetchData")
    }

    // SwiftAsyncSequenceGenerator validation tests

    @Test
    fun `async sequence generator throws for blank name`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "",
            parameters = emptyList(),
            elementType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncSequenceGenerator.generate(spec)
        }
        assertContains(exception.message!!, "blank")
    }

    @Test
    fun `async sequence generator throws for blank parameter name`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "getUpdates",
            parameters = listOf(SwiftParameter("", SwiftType.Named("Int"))),
            elementType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncSequenceGenerator.generate(spec)
        }
        assertContains(exception.message!!, "Parameter name cannot be blank")
    }

    @Test
    fun `async sequence generator succeeds with valid spec`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "getUpdates",
            parameters = emptyList(),
            elementType = SwiftType.Named("String")
        )
        val result = asyncSequenceGenerator.generate(spec)
        assertContains(result, "func getUpdates")
        assertContains(result, "AsyncStream")
    }
}
