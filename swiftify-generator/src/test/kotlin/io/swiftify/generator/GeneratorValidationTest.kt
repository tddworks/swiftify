package io.swiftify.generator

import io.swiftify.swift.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains

/**
 * Tests for generator validation and error handling.
 */
class GeneratorValidationTest {

    private val enumGenerator = SwiftEnumGenerator()
    private val defaultsGenerator = SwiftDefaultsGenerator()
    private val asyncStreamGenerator = SwiftAsyncStreamGenerator()

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

    // SwiftDefaultsGenerator validation tests

    @Test
    fun `async function generator throws for blank name`() {
        val spec = SwiftDefaultsSpec(
            name = "",
            parameters = emptyList(),
            returnType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            defaultsGenerator.generate(spec)
        }
        assertContains(exception.message!!, "blank")
    }

    @Test
    fun `async function generator throws for blank parameter name`() {
        val spec = SwiftDefaultsSpec(
            name = "fetchData",
            parameters = listOf(SwiftParameter("", SwiftType.Named("Int"))),
            returnType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            defaultsGenerator.generate(spec)
        }
        assertContains(exception.message!!, "Parameter name cannot be blank")
    }

    @Test
    fun `async function generator succeeds with valid spec`() {
        val spec = SwiftDefaultsSpec(
            name = "fetchData",
            parameters = listOf(SwiftParameter("id", SwiftType.Named("Int"))),
            returnType = SwiftType.Named("String")
        )
        val result = defaultsGenerator.generate(spec)
        assertContains(result, "func fetchData")
    }

    // SwiftAsyncStreamGenerator validation tests

    @Test
    fun `async sequence generator throws for blank name`() {
        val spec = SwiftAsyncStreamSpec(
            name = "",
            parameters = emptyList(),
            elementType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncStreamGenerator.generate(spec)
        }
        assertContains(exception.message!!, "blank")
    }

    @Test
    fun `async sequence generator throws for blank parameter name`() {
        val spec = SwiftAsyncStreamSpec(
            name = "getUpdates",
            parameters = listOf(SwiftParameter("", SwiftType.Named("Int"))),
            elementType = SwiftType.Named("String")
        )
        val exception = assertThrows<SwiftifyValidationException> {
            asyncStreamGenerator.generate(spec)
        }
        assertContains(exception.message!!, "Parameter name cannot be blank")
    }

    @Test
    fun `async sequence generator succeeds with valid spec`() {
        val spec = SwiftAsyncStreamSpec(
            name = "getUpdates",
            parameters = emptyList(),
            elementType = SwiftType.Named("String")
        )
        val result = asyncStreamGenerator.generate(spec)
        assertContains(result, "func getUpdates")
        assertContains(result, "AsyncStream")
    }
}
