package io.swiftify.generator

import io.swiftify.swift.SwiftDefaultsSpec
import io.swiftify.swift.SwiftParameter
import io.swiftify.swift.SwiftType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift async function code generation.
 */
class SwiftDefaultsGeneratorTest {
    private val generator = SwiftDefaultsGenerator()

    @Test
    fun `generates simple async function`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetchData",
                parameters = emptyList(),
                returnType = SwiftType.Named("String"),
            )

        val result = generator.generate(spec)

        val expected = "public func fetchData() async -> String"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async throwing function`() {
        val spec =
            SwiftDefaultsSpec(
                name = "loadResource",
                parameters =
                listOf(
                    SwiftParameter(name = "url", type = SwiftType.Named("URL")),
                ),
                returnType = SwiftType.Named("Data"),
                isThrowing = true,
            )

        val result = generator.generate(spec)

        val expected = "public func loadResource(url: URL) async throws -> Data"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with void return`() {
        val spec =
            SwiftDefaultsSpec(
                name = "saveData",
                parameters =
                listOf(
                    SwiftParameter(name = "data", type = SwiftType.Named("Data")),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generate(spec)

        val expected = "public func saveData(data: Data) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with multiple parameters`() {
        val spec =
            SwiftDefaultsSpec(
                name = "search",
                parameters =
                listOf(
                    SwiftParameter(name = "query", type = SwiftType.Named("String")),
                    SwiftParameter(name = "limit", type = SwiftType.Named("Int")),
                    SwiftParameter(name = "offset", type = SwiftType.Named("Int")),
                ),
                returnType = SwiftType.Array(SwiftType.Named("Result")),
            )

        val result = generator.generate(spec)

        val expected = "public func search(query: String, limit: Int, offset: Int) async -> [Result]"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with external parameter names`() {
        val spec =
            SwiftDefaultsSpec(
                name = "move",
                parameters =
                listOf(
                    SwiftParameter(
                        name = "point",
                        externalName = "to",
                        type = SwiftType.Named("Point"),
                    ),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generate(spec)

        val expected = "public func move(to point: Point) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with no argument label`() {
        val spec =
            SwiftDefaultsSpec(
                name = "print",
                parameters =
                listOf(
                    SwiftParameter(
                        name = "message",
                        externalName = "_",
                        type = SwiftType.Named("String"),
                    ),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generate(spec)

        val expected = "public func print(_ message: String) async"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates generic async function`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetch",
                typeParameters = listOf("T"),
                parameters =
                listOf(
                    SwiftParameter(name = "request", type = SwiftType.Named("Request")),
                ),
                returnType = SwiftType.Generic("T"),
            )

        val result = generator.generate(spec)

        val expected = "public func fetch<T>(request: Request) async -> T"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates async function with default parameter values`() {
        val spec =
            SwiftDefaultsSpec(
                name = "query",
                parameters =
                listOf(
                    SwiftParameter(name = "sql", type = SwiftType.Named("String")),
                    SwiftParameter(
                        name = "timeout",
                        type = SwiftType.Named("Int"),
                        defaultValue = "30",
                    ),
                ),
                returnType = SwiftType.Array(SwiftType.Named("Row")),
            )

        val result = generator.generate(spec)

        val expected = "public func query(sql: String, timeout: Int = 30) async -> [Row]"
        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates internal async function`() {
        val spec =
            SwiftDefaultsSpec(
                name = "internalFetch",
                parameters = emptyList(),
                returnType = SwiftType.Named("Data"),
                accessLevel = SwiftDefaultsSpec.AccessLevel.INTERNAL,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("internal func"))
    }

    @Test
    fun `generates async function with optional return type`() {
        val spec =
            SwiftDefaultsSpec(
                name = "findUser",
                parameters =
                listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("Int")),
                ),
                returnType = SwiftType.Optional(SwiftType.Named("User")),
            )

        val result = generator.generate(spec)

        val expected = "public func findUser(id: Int) async -> User?"
        assertEquals(expected, result.trim())
    }

    // Tests for generateConvenienceOverloads (Kotlin 1.8+ compatible approach)
    // Since Kotlin 1.8+ generates async/await natively, we only generate convenience overloads

    @Test
    fun `convenience overloads returns empty for function without defaults`() {
        val spec =
            SwiftDefaultsSpec(
                name = "simpleFunc",
                parameters =
                listOf(
                    SwiftParameter(name = "value", type = SwiftType.Named("Int")),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generateConvenienceOverloads(spec, "MyClass")

        // No default params, Kotlin already provides the method
        assertEquals("", result)
    }

    @Test
    fun `convenience overloads generates distinct overloads for single default param`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetchUser",
                parameters =
                listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("String")),
                    SwiftParameter(
                        name = "includeProfile",
                        type = SwiftType.Named("Bool"),
                        defaultValue = "true",
                    ),
                ),
                returnType = SwiftType.Named("User"),
                isThrowing = true,
            )

        val result = generator.generateConvenienceOverloads(spec, "MyClass")

        // Should generate ONE convenience overload with just 'id'
        // (The full signature is provided by Kotlin)
        assertTrue(result.contains("extension MyClass"))
        assertTrue(result.contains("func fetchUser(id: String) async throws -> User"))
        // The overload calls the full Kotlin method with default values
        assertTrue(result.contains("try await fetchUser(id: id, includeProfile: true)"))
        // Should NOT contain the full signature (Kotlin provides it)
        assertFalse(result.contains("includeProfile: Bool)"))
    }

    @Test
    fun `convenience overloads generates multiple overloads for multiple defaults`() {
        val spec =
            SwiftDefaultsSpec(
                name = "search",
                parameters =
                listOf(
                    SwiftParameter(name = "query", type = SwiftType.Named("String")),
                    SwiftParameter(
                        name = "limit",
                        type = SwiftType.Named("Int"),
                        defaultValue = "10",
                    ),
                    SwiftParameter(
                        name = "offset",
                        type = SwiftType.Named("Int"),
                        defaultValue = "0",
                    ),
                ),
                returnType = SwiftType.Array(SwiftType.Named("Result")),
                isThrowing = true,
            )

        val result = generator.generateConvenienceOverloads(spec, "SearchService")

        // Should generate 2 overloads:
        // 1. search(query:) -> calls search(query, 10, 0)
        // 2. search(query:, limit:) -> calls search(query, limit, 0)
        assertTrue(result.contains("func search(query: String) async throws -> [Result]"))
        assertTrue(result.contains("func search(query: String, limit: Int) async throws -> [Result]"))
        // Both call the full method with defaults filled in
        assertTrue(result.contains("search(query: query, limit: 10, offset: 0)"))
        assertTrue(result.contains("search(query: query, limit: limit, offset: 0)"))
    }

    @Test
    fun `convenience overload bodies returns list of function bodies`() {
        val spec =
            SwiftDefaultsSpec(
                name = "getData",
                parameters =
                listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("Int")),
                    SwiftParameter(
                        name = "cache",
                        type = SwiftType.Named("Bool"),
                        defaultValue = "true",
                    ),
                ),
                returnType = SwiftType.Named("Data"),
                isThrowing = true,
            )

        val result = generator.generateConvenienceOverloadBodies(spec)

        // Should return a list with one overload body
        assertEquals(1, result.size)
        assertTrue(result[0].contains("func getData(id: Int) async throws -> Data"))
        assertTrue(result[0].contains("try await getData(id: id, cache: true)"))
    }

    @Test
    fun `convenience overload bodies returns empty list when no defaults`() {
        val spec =
            SwiftDefaultsSpec(
                name = "noDefaults",
                parameters =
                listOf(
                    SwiftParameter(name = "a", type = SwiftType.Named("Int")),
                    SwiftParameter(name = "b", type = SwiftType.Named("String")),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generateConvenienceOverloadBodies(spec)

        // No defaults, nothing to generate
        assertTrue(result.isEmpty())
    }

    @Test
    fun `convenience overloads without class name generates top-level functions`() {
        val spec =
            SwiftDefaultsSpec(
                name = "topLevelFunc",
                parameters =
                listOf(
                    SwiftParameter(name = "x", type = SwiftType.Named("Int")),
                    SwiftParameter(
                        name = "y",
                        type = SwiftType.Named("Int"),
                        defaultValue = "0",
                    ),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generateConvenienceOverloads(spec, className = null)

        // Should NOT have extension wrapper
        assertFalse(result.contains("extension"))
        // Should have the function
        assertTrue(result.contains("func topLevelFunc(x: Int) async"))
    }
}
