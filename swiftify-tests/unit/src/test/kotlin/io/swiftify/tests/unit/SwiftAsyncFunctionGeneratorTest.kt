package io.swiftify.tests.unit

import io.swiftify.common.SwiftAsyncFunctionSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import io.swiftify.generator.SwiftAsyncFunctionGenerator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // Tests for generateWithOverloads (Swift default parameters)

    @Test
    fun `generates single function with default parameter`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "fetchUser",
            parameters = listOf(
                SwiftParameter(name = "id", type = SwiftType.Named("String")),
                SwiftParameter(
                    name = "includeProfile",
                    type = SwiftType.Named("Bool"),
                    defaultValue = "true"
                )
            ),
            returnType = SwiftType.Named("User"),
            isThrowing = true
        )

        val result = generator.generateWithOverloads(spec)

        // Should generate single function with Swift default parameter
        assertTrue(result.contains("func fetchUser(id: String, includeProfile: Bool = true) async throws -> User"))
        // Only one function, not multiple overloads
        assertEquals(1, result.lines().filter { it.contains("func fetchUser") }.size)
    }

    @Test
    fun `generates single function with multiple default parameters`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "search",
            parameters = listOf(
                SwiftParameter(name = "query", type = SwiftType.Named("String")),
                SwiftParameter(
                    name = "limit",
                    type = SwiftType.Named("Int"),
                    defaultValue = "10"
                ),
                SwiftParameter(
                    name = "offset",
                    type = SwiftType.Named("Int"),
                    defaultValue = "0"
                )
            ),
            returnType = SwiftType.Array(SwiftType.Named("Result")),
            isThrowing = true
        )

        val result = generator.generateWithOverloads(spec)

        // Should generate single function with all Swift default parameters
        assertTrue(result.contains("func search(query: String, limit: Int = 10, offset: Int = 0) async throws -> [Result]"))
        // Only one function
        assertEquals(1, result.lines().filter { it.contains("func search") }.size)
    }

    @Test
    fun `generates single function without defaults when none specified`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "simpleFunc",
            parameters = listOf(
                SwiftParameter(name = "value", type = SwiftType.Named("Int"))
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generateWithOverloads(spec)

        // Should only have one function
        assertEquals(1, result.lines().filter { it.contains("func simpleFunc") }.size)
        assertTrue(result.contains("func simpleFunc(value: Int) async"))
    }

    @Test
    fun `generates function with many default parameters`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "manyDefaults",
            parameters = listOf(
                SwiftParameter(name = "a", type = SwiftType.Named("Int")),
                SwiftParameter(name = "b", type = SwiftType.Named("Int"), defaultValue = "1"),
                SwiftParameter(name = "c", type = SwiftType.Named("Int"), defaultValue = "2"),
                SwiftParameter(name = "d", type = SwiftType.Named("Int"), defaultValue = "3")
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generateWithOverloads(spec)

        // Should generate single function with all defaults
        assertTrue(result.contains("a: Int"))
        assertTrue(result.contains("b: Int = 1"))
        assertTrue(result.contains("c: Int = 2"))
        assertTrue(result.contains("d: Int = 3"))
        // Only one function
        assertEquals(1, result.lines().filter { it.contains("func manyDefaults") }.size)
    }

    @Test
    fun `includes default values in Swift signature`() {
        val spec = SwiftAsyncFunctionSpec(
            name = "withDefault",
            parameters = listOf(
                SwiftParameter(name = "x", type = SwiftType.Named("Int")),
                SwiftParameter(
                    name = "y",
                    type = SwiftType.Named("Int"),
                    defaultValue = "42"
                )
            ),
            returnType = SwiftType.Void
        )

        val result = generator.generateWithOverloads(spec)

        // Swift default parameter should include the default value
        assertTrue(result.contains("y: Int = 42"))
    }
}
