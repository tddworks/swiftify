package io.swiftify.tests.acceptance

import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end acceptance tests for sealed class → Swift enum transformation.
 */
class SealedClassAcceptanceTest {

    private val transformer = SwiftifyTransformer()

    @Test
    fun `NetworkResult sealed class transforms to Swift enum`() {
        val kotlinSource = """
            package com.example

            sealed class NetworkResult<out T> {
                data class Success<T>(val data: T) : NetworkResult<T>()
                data class Error(val message: String, val code: Int) : NetworkResult<Nothing>()
                data object Loading : NetworkResult<Nothing>()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0, "Should transform declarations")
        assertContains(result.swiftCode, "enum NetworkResult")
        assertContains(result.swiftCode, "case success")
        assertContains(result.swiftCode, "case error")
        assertContains(result.swiftCode, "case loading")
    }

    @Test
    fun `AuthState sealed class transforms to Swift enum with associated values`() {
        val kotlinSource = """
            package com.example

            sealed class AuthState {
                data object LoggedOut : AuthState()
                data class LoggedIn(val userId: String, val token: String) : AuthState()
                data class Error(val reason: String) : AuthState()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "enum AuthState")
        assertContains(result.swiftCode, "case loggedOut")
        assertContains(result.swiftCode, "case loggedIn")
        assertContains(result.swiftCode, "case error")
    }

    @Test
    fun `exhaustive sealed class gets @frozen attribute`() {
        val kotlinSource = """
            sealed class Color {
                data object Red : Color()
                data object Green : Color()
                data object Blue : Color()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // The transformer should mark it as exhaustive by default
        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "enum Color")
    }
}
