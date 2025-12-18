package io.swiftify.tests.acceptance

import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * End-to-end acceptance tests for suspend function → Swift async transformation.
 */
class SuspendFunctionAcceptanceTest {

    private val transformer = SwiftifyTransformer()

    @Test
    fun `simple suspend function transforms to async`() {
        val kotlinSource = """
            suspend fun fetchUser(id: String): User {
                return User(id)
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func fetchUser")
        assertContains(result.swiftCode, "async")
    }

    @Test
    fun `suspend function with multiple parameters transforms correctly`() {
        val kotlinSource = """
            suspend fun login(username: String, password: String): AuthResult {
                return AuthResult.Success
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func login")
        assertContains(result.swiftCode, "username")
        assertContains(result.swiftCode, "password")
        assertContains(result.swiftCode, "async")
    }

    @Test
    fun `suspend function returning Unit transforms correctly`() {
        val kotlinSource = """
            suspend fun logout() {
                // logout logic
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func logout")
        assertContains(result.swiftCode, "async")
    }

    @Test
    fun `throwing suspend function has throws modifier`() {
        val kotlinSource = """
            suspend fun riskyOperation(): String {
                throw Exception("Error")
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "async")
        assertContains(result.swiftCode, "throws")
    }
}
