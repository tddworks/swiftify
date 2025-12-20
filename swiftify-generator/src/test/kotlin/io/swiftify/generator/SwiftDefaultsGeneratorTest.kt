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

    // ============================================================
    // Implementation Generation Tests
    // ============================================================

    @Test
    fun `generateWithImplementation wraps in extension for class`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetchData",
                parameters = emptyList(),
                returnType = SwiftType.Named("Data"),
                isThrowing = true,
            )

        val result = generator.generateWithImplementation(spec, "Repository")

        assertTrue(result.contains("extension Repository"))
        assertTrue(result.contains("public func fetchData()"))
    }

    @Test
    fun `generateWithImplementation includes continuation pattern`() {
        val spec =
            SwiftDefaultsSpec(
                name = "loadUser",
                parameters = listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("String")),
                ),
                returnType = SwiftType.Named("User"),
                isThrowing = true,
            )

        val result = generator.generateWithImplementation(spec, "UserService")

        assertTrue(result.contains("withCheckedThrowingContinuation"))
        assertTrue(result.contains("continuation.resume"))
    }

    @Test
    fun `generateWithImplementation for void return handles void properly`() {
        val spec =
            SwiftDefaultsSpec(
                name = "saveData",
                parameters = listOf(
                    SwiftParameter(name = "data", type = SwiftType.Named("Data")),
                ),
                returnType = SwiftType.Void,
                isThrowing = true,
            )

        val result = generator.generateWithImplementation(spec, "DataStore")

        // Void functions should use continuation that returns ()
        assertTrue(result.contains("continuation.resume"))
    }

    @Test
    fun `generateWithImplementation calls self for class methods`() {
        val spec =
            SwiftDefaultsSpec(
                name = "process",
                parameters = emptyList(),
                returnType = SwiftType.Named("Result"),
                isThrowing = true,
            )

        val result = generator.generateWithImplementation(spec, "Processor")

        assertTrue(result.contains("self.process"))
    }

    @Test
    fun `generateWithImplementation without class has no self prefix`() {
        val spec =
            SwiftDefaultsSpec(
                name = "globalFunc",
                parameters = emptyList(),
                returnType = SwiftType.Named("String"),
                isThrowing = true,
            )

        val result = generator.generateWithImplementation(spec, null)

        assertFalse(result.contains("extension"))
        assertTrue(result.contains("globalFunc("))
    }

    // ============================================================
    // Function Body Generation Tests
    // ============================================================

    @Test
    fun `generateFunctionBody includes proper indentation`() {
        val spec =
            SwiftDefaultsSpec(
                name = "compute",
                parameters = emptyList(),
                returnType = SwiftType.Named("Int"),
                isThrowing = true,
            )

        val result = generator.generateFunctionBody(spec)

        // Should have consistent indentation
        assertTrue(result.contains("    "))
    }

    @Test
    fun `generateFunctionBody includes completionHandler callback`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetch",
                parameters = emptyList(),
                returnType = SwiftType.Named("Data"),
                isThrowing = true,
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("completionHandler:"))
    }

    @Test
    fun `generateFunctionBody handles error path for throwing functions`() {
        val spec =
            SwiftDefaultsSpec(
                name = "riskyOperation",
                parameters = emptyList(),
                returnType = SwiftType.Named("Result"),
                isThrowing = true,
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("if let error = error"))
        assertTrue(result.contains("continuation.resume(throwing: error)"))
    }

    @Test
    fun `generateFunctionBody handles non-throwing functions`() {
        val spec =
            SwiftDefaultsSpec(
                name = "safeOperation",
                parameters = emptyList(),
                returnType = SwiftType.Named("String"),
                isThrowing = false,
                isAsync = true,
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("withCheckedContinuation"))
        assertFalse(result.contains("withCheckedThrowingContinuation"))
    }

    // ============================================================
    // Convenience Overload Edge Cases
    // ============================================================

    @Test
    fun `convenience overloads respects maxOverloads limit`() {
        val spec =
            SwiftDefaultsSpec(
                name = "manyDefaults",
                parameters = listOf(
                    SwiftParameter(name = "a", type = SwiftType.Named("Int")),
                    SwiftParameter(name = "b", type = SwiftType.Named("Int"), defaultValue = "1"),
                    SwiftParameter(name = "c", type = SwiftType.Named("Int"), defaultValue = "2"),
                    SwiftParameter(name = "d", type = SwiftType.Named("Int"), defaultValue = "3"),
                    SwiftParameter(name = "e", type = SwiftType.Named("Int"), defaultValue = "4"),
                    SwiftParameter(name = "f", type = SwiftType.Named("Int"), defaultValue = "5"),
                    SwiftParameter(name = "g", type = SwiftType.Named("Int"), defaultValue = "6"),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generateConvenienceOverloads(spec, "TestClass", maxOverloads = 3)

        // Count number of function definitions
        val funcCount = "public func manyDefaults".toRegex().findAll(result).count()
        assertTrue(funcCount <= 3, "Expected at most 3 overloads, got $funcCount")
    }

    @Test
    fun `convenience overloads handles all defaults at start`() {
        val spec =
            SwiftDefaultsSpec(
                name = "allDefaults",
                parameters = listOf(
                    SwiftParameter(name = "a", type = SwiftType.Named("Int"), defaultValue = "1"),
                    SwiftParameter(name = "b", type = SwiftType.Named("Int"), defaultValue = "2"),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generateConvenienceOverloads(spec, "TestClass")

        // Should generate no-arg overload
        assertTrue(result.contains("func allDefaults()"))
    }

    @Test
    fun `convenience overload bodies preserves access level`() {
        val spec =
            SwiftDefaultsSpec(
                name = "internalFunc",
                parameters = listOf(
                    SwiftParameter(name = "x", type = SwiftType.Named("Int")),
                    SwiftParameter(name = "y", type = SwiftType.Named("Int"), defaultValue = "0"),
                ),
                returnType = SwiftType.Void,
                accessLevel = SwiftDefaultsSpec.AccessLevel.INTERNAL,
            )

        val result = generator.generateConvenienceOverloadBodies(spec)

        assertTrue(result.isNotEmpty())
        assertTrue(result[0].contains("internal func"))
    }

    @Test
    fun `convenience overload handles nil default for optional`() {
        val spec =
            SwiftDefaultsSpec(
                name = "optionalParam",
                parameters = listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("String")),
                    SwiftParameter(
                        name = "extra",
                        type = SwiftType.Optional(SwiftType.Named("String")),
                        defaultValue = "nil",
                    ),
                ),
                returnType = SwiftType.Named("Result"),
                isThrowing = true,
            )

        val result = generator.generateConvenienceOverloads(spec, "Service")

        assertTrue(result.contains("extra: nil"))
    }

    // ============================================================
    // Type Handling Tests
    // ============================================================

    @Test
    fun `generates function with dictionary return type`() {
        val spec =
            SwiftDefaultsSpec(
                name = "getMapping",
                parameters = emptyList(),
                returnType = SwiftType.Dictionary(
                    SwiftType.Named("String"),
                    SwiftType.Named("Int"),
                ),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("[String: Int]"))
    }

    @Test
    fun `generates function with nested generic type`() {
        val spec =
            SwiftDefaultsSpec(
                name = "getNestedList",
                parameters = emptyList(),
                returnType = SwiftType.Array(
                    SwiftType.Array(SwiftType.Named("String")),
                ),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("[[String]]"))
    }

    @Test
    fun `generates function with variadic parameter marker`() {
        val spec =
            SwiftDefaultsSpec(
                name = "printAll",
                parameters = listOf(
                    SwiftParameter(
                        name = "items",
                        type = SwiftType.Named("String"),
                        isVariadic = true,
                    ),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("items: String..."))
    }

    @Test
    fun `generates function with inout parameter`() {
        val spec =
            SwiftDefaultsSpec(
                name = "modify",
                parameters = listOf(
                    SwiftParameter(
                        name = "value",
                        type = SwiftType.Named("Int"),
                        isInout = true,
                    ),
                ),
                returnType = SwiftType.Void,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("inout Int"))
    }

    // ============================================================
    // Validation Tests
    // ============================================================

    @Test
    fun `generate throws for blank function name`() {
        val spec =
            SwiftDefaultsSpec(
                name = "",
                parameters = emptyList(),
                returnType = SwiftType.Void,
            )

        val exception = org.junit.jupiter.api.assertThrows<io.swiftify.swift.SwiftifyValidationException> {
            generator.generate(spec)
        }

        assertTrue(exception.message?.contains("name") == true)
    }

    @Test
    fun `generate throws for blank parameter name`() {
        val spec =
            SwiftDefaultsSpec(
                name = "validFunc",
                parameters = listOf(
                    SwiftParameter(name = "", type = SwiftType.Named("Int")),
                ),
                returnType = SwiftType.Void,
            )

        val exception = org.junit.jupiter.api.assertThrows<io.swiftify.swift.SwiftifyValidationException> {
            generator.generate(spec)
        }

        assertTrue(exception.message?.contains("Parameter") == true)
    }

    // ============================================================
    // Private Access Level Tests
    // ============================================================

    @Test
    fun `generates private function`() {
        val spec =
            SwiftDefaultsSpec(
                name = "privateHelper",
                parameters = emptyList(),
                returnType = SwiftType.Named("Int"),
                accessLevel = SwiftDefaultsSpec.AccessLevel.PRIVATE,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("private func"))
    }
}
