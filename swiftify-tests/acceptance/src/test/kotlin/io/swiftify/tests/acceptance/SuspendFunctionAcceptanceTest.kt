package io.swiftify.tests.acceptance

import io.swiftify.dsl.swiftify
import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end acceptance tests for suspend function → Swift async transformation.
 *
 * NOTE: Kotlin 2.0+ automatically generates async/await for suspend functions.
 * Swiftify only generates CONVENIENCE OVERLOADS for functions WITH default parameters.
 * Functions without default parameters don't need transformation (Kotlin handles them).
 */
class SuspendFunctionAcceptanceTest {
    private val transformer = SwiftifyTransformer()

    // DSL mode config - process all functions without annotations
    private val dslConfig =
        swiftify {
            defaults {
                requireAnnotations = false
            }
        }

    @Test
    fun `suspend function WITHOUT defaults is not transformed`() {
        val kotlinSource =
            """
            suspend fun fetchUser(id: String): User {
                return User(id)
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // No default parameters, so no transformation needed
        // Kotlin 2.0+ already provides the async function
        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `suspend function WITH defaults generates convenience overload`() {
        val kotlinSource =
            """
            suspend fun fetchUser(id: String, limit: Int = 10): User {
                return User(id)
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // Has default parameter, so generates convenience overload
        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func fetchUser")
        // Should have overload without limit parameter
        assertContains(result.swiftCode, "fetchUser(id: id, limit: 10)")
    }

    @Test
    fun `suspend function with multiple default parameters generates overloads`() {
        val kotlinSource =
            """
            suspend fun login(username: String, password: String, rememberMe: Boolean = false): AuthResult {
                return AuthResult.Success
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func login")
        assertContains(result.swiftCode, "rememberMe: false")
    }

    @Test
    fun `suspend function returning Unit with defaults transforms correctly`() {
        val kotlinSource =
            """
            suspend fun logout(force: Boolean = false) {
                // logout logic
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func logout")
        assertContains(result.swiftCode, "force: false")
    }

    @Test
    fun `throwing suspend function with defaults has async throws`() {
        val kotlinSource =
            """
            suspend fun riskyOperation(retries: Int = 3): String {
                throw Exception("Error")
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "async throws")
    }
}
