package io.swiftify.tests.unit

import io.swiftify.common.SwiftAsyncFunctionSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import io.swiftify.generator.SwiftAsyncFunctionGenerator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift async function code generation.
 */
class SwiftAsyncFunctionGeneratorTest {

    private val generator = SwiftAsyncFunctionGenerator()

    @Test
    fun `generates simple async function`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "fetchData",
            parameters = emptyList(),
            returnType = SwiftType.Named("String")
        )

        val result = generator.generate(spec)

        val expected = "public func fetchData() async -> String"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async throwing function`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "loadResource",
            parameters = listOf(
                SwiftParameter(name = "url", type = SwiftType.Named("URL"))
            ),
            returnType = SwiftType.Named("Data"),
            isThrowing = true
        )

        val result = generator.generate(spec)

        val expected = "public func loadResource(url: URL) async throws -> Data"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with void return`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "saveData",
            parameters = listOf(
                SwiftParameter(name = "data", type = SwiftType.Named("Data"))
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generate(spec)

        val expected = "public func saveData(data: Data) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with multiple parameters`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "search",
            parameters = listOf(
                SwiftParameter(name = "query", type = SwiftType.Named("String")),
                SwiftParameter(name = "limit", type = SwiftType.Named("Int")),
                SwiftParameter(name = "offset", type = SwiftType.Named("Int"))
            ),
            returnType = SwiftType.Array(SwiftType.Named("Result"))
        )

        val result = generator.generate(spec)

        val expected = "public func search(query: String, limit: Int, offset: Int) async -> [Result]"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with external parameter names`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "move",
            parameters = listOf(
                SwiftParameter(
                    name = "point",
                    externalName = "to",
                    type = SwiftType.Named("Point")
                )
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generate(spec)

        val expected = "public func move(to point: Point) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with no argument label`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "print",
            parameters = listOf(
                SwiftParameter(
                    name = "message",
                    externalName = "_",
                    type = SwiftType.Named("String")
                )
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generate(spec)

        val expected = "public func print(_ message: String) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates generic async function`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "fetch",
            typeParameters = listOf("T"),
            parameters = listOf(
                SwiftParameter(name = "request", type = SwiftType.Named("Request"))
            ),
            returnType = SwiftType.Generic("T")
        )

        val result = generator.generate(spec)

        val expected = "public func fetch<T>(request: Request) async -> T"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with default parameter values`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "query",
            parameters = listOf(
                SwiftParameter(name = "sql", type = SwiftType.Named("String")),
                SwiftParameter(
                    name = "timeout",
                    type = SwiftType.Named("Int"),
                    defaultValue = "30"
                )
            ),
            returnType = SwiftType.Array(SwiftType.Named("Row"))
        )

        val result = generator.generate(spec)

        val expected = "public func query(sql: String, timeout: Int = 30) async -> [Row]"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates internal async function`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "internalFetch",
            parameters = emptyList(),
            returnType = SwiftType.Named("Data"),
            accessLevel = SwiftAsyncFunctionSpec.AccessLevel.INTERNAL
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("internal func"))
    }

    @Test
    fun `generates async function with optional return type`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "findUser",
            parameters = listOf(
                SwiftParameter(name = "id", type = SwiftType.Named("Int"))
            ),
            returnType = SwiftType.Optional(SwiftType.Named("User"))
        )

        val result = generator.generate(spec)

        val expected = "public func findUser(id: Int) async -> User?"
        assertEquals(expected, result.trim())
    }
}
