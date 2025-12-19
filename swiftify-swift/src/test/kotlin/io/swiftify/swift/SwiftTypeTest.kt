package io.swiftify.swift

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift type representations.
 */
class SwiftTypeTest {

    @Test
    fun `named type represents simple Swift types`() {
        val stringType = SwiftType.Named("String")
        assertEquals("String", stringType.name)
        assertEquals("String", stringType.swiftRepresentation)
    }

    @Test
    fun `optional type wraps another type`() {
        val optionalString = SwiftType.Optional(SwiftType.Named("String"))
        assertEquals("String?", optionalString.swiftRepresentation)
    }

    @Test
    fun `array type represents Swift arrays`() {
        val arrayOfInt = SwiftType.Array(SwiftType.Named("Int"))
        assertEquals("[Int]", arrayOfInt.swiftRepresentation)
    }

    @Test
    fun `dictionary type represents Swift dictionaries`() {
        val dict = SwiftType.Dictionary(
            keyType = SwiftType.Named("String"),
            valueType = SwiftType.Named("Int")
        )
        assertEquals("[String: Int]", dict.swiftRepresentation)
    }

    @Test
    fun `generic type represents type parameters`() {
        val generic = SwiftType.Generic("T")
        assertEquals("T", generic.name)
        assertEquals("T", generic.swiftRepresentation)
    }

    @Test
    fun `parameterized type represents generic types with arguments`() {
        val resultType = SwiftType.Parameterized(
            base = "Result",
            arguments = listOf(SwiftType.Named("String"), SwiftType.Named("Error"))
        )
        assertEquals("Result<String, Error>", resultType.swiftRepresentation)
    }

    @Test
    fun `function type represents closures`() {
        val closure = SwiftType.Function(
            parameters = listOf(SwiftType.Named("Int"), SwiftType.Named("String")),
            returnType = SwiftType.Named("Bool")
        )
        assertEquals("(Int, String) -> Bool", closure.swiftRepresentation)
    }

    @Test
    fun `async function type`() {
        val asyncClosure = SwiftType.Function(
            parameters = listOf(SwiftType.Named("String")),
            returnType = SwiftType.Named("Data"),
            isAsync = true
        )
        assertEquals("(String) async -> Data", asyncClosure.swiftRepresentation)
    }

    @Test
    fun `throwing function type`() {
        val throwingClosure = SwiftType.Function(
            parameters = emptyList(),
            returnType = SwiftType.Named("String"),
            isThrowing = true
        )
        assertEquals("() throws -> String", throwingClosure.swiftRepresentation)
    }

    @Test
    fun `async throwing function type`() {
        val asyncThrowingClosure = SwiftType.Function(
            parameters = listOf(SwiftType.Named("URL")),
            returnType = SwiftType.Named("Data"),
            isAsync = true,
            isThrowing = true
        )
        assertEquals("(URL) async throws -> Data", asyncThrowingClosure.swiftRepresentation)
    }

    @Test
    fun `void type`() {
        val void = SwiftType.Void
        assertEquals("Void", void.swiftRepresentation)
    }

    @Test
    fun `tuple type`() {
        val tuple = SwiftType.Tuple(
            elements = listOf(
                SwiftType.Named("String"),
                SwiftType.Named("Int")
            )
        )
        assertEquals("(String, Int)", tuple.swiftRepresentation)
    }

    @Test
    fun `nested optional types`() {
        val nestedOptional = SwiftType.Optional(
            SwiftType.Optional(SwiftType.Named("String"))
        )
        assertEquals("String??", nestedOptional.swiftRepresentation)
    }

    @Test
    fun `complex nested type`() {
        val complexType = SwiftType.Dictionary(
            keyType = SwiftType.Named("String"),
            valueType = SwiftType.Array(
                SwiftType.Optional(SwiftType.Named("Int"))
            )
        )
        assertEquals("[String: [Int?]]", complexType.swiftRepresentation)
    }
}
