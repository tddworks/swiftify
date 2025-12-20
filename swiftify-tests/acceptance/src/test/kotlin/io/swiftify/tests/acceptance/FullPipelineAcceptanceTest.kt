package io.swiftify.tests.acceptance

import io.swiftify.dsl.swiftify
import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Full end-to-end pipeline test using realistic sample code.
 *
 * NOTE: Swiftify generates convenience code for:
 * - Functions WITH default parameters (@SwiftDefaults or DSL mode)
 * - Flow functions (@SwiftFlow or DSL mode)
 * - Sealed classes WITH @SwiftEnum annotation
 *
 * Functions WITHOUT default parameters don't need transformation (Kotlin 2.0+ handles them).
 * Sealed classes WITHOUT @SwiftEnum don't need transformation (Kotlin/Native exports them).
 */
class FullPipelineAcceptanceTest {
    private val transformer = SwiftifyTransformer()

    // DSL mode config - process all functions without annotations
    private val dslConfig =
        swiftify {
            defaults {
                requireAnnotations = false
            }
        }

    @Test
    fun `full UserRepository transforms correctly`() {
        val kotlinSource =
            """
            package com.example

            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.StateFlow
            import kotlinx.coroutines.flow.MutableStateFlow

            class UserRepository {
                private val _currentUser = MutableStateFlow<User?>(null)

                val currentUser: StateFlow<User?> = _currentUser

                // No defaults - won't be transformed (Kotlin 2.0+ handles it)
                suspend fun fetchUser(id: String): User {
                    return User(id = id, name = "John Doe", email = "john@example.com")
                }

                // Has defaults - WILL be transformed (convenience overloads)
                suspend fun fetchUserWithOptions(
                    id: String,
                    includeProfile: Boolean = true,
                    limit: Int = 10
                ): User {
                    return User(id = id, name = "John", email = "john@example.com")
                }

                // Flow function - WILL be transformed (AsyncStream wrapper)
                fun getUserUpdates(userId: String): Flow<User> {
                    throw NotImplementedError("Stub")
                }

                // No defaults - won't be transformed
                suspend fun login(username: String, password: String): NetworkResult<User> {
                    return NetworkResult.Success(User("1", username, "${'$'}username@example.com"))
                }

                // No defaults - won't be transformed
                suspend fun logout() {
                    _currentUser.value = null
                }
            }

            data class User(
                val id: String,
                val name: String,
                val email: String
            )
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        println("=== Generated Swift Code ===")
        println(result.swiftCode)
        println("=== End ===")

        // Should transform: fetchUserWithOptions (has defaults) + getUserUpdates (Flow)
        assertTrue(result.declarationsTransformed >= 2, "Should transform at least 2 declarations")

        // Check function with defaults is transformed
        assertContains(result.swiftCode, "func fetchUserWithOptions")
        assertContains(result.swiftCode, "includeProfile: true")

        // Check Flow functions are transformed
        assertContains(result.swiftCode, "getUserUpdates")
        assertContains(result.swiftCode, "AsyncStream")
    }

    @Test
    fun `full NetworkResult sealed class with SwiftEnum transforms correctly`() {
        val kotlinSource =
            """
            package com.example

            @SwiftEnum
            sealed class NetworkResult<out T> {
                data class Success<T>(val data: T) : NetworkResult<T>()
                data class Error(val message: String, val code: Int) : NetworkResult<Nothing>()
                data object Loading : NetworkResult<Nothing>()
            }

            @SwiftEnum
            sealed class AuthState {
                data object LoggedOut : AuthState()
                data class LoggedIn(val userId: String, val token: String) : AuthState()
                data class Error(val reason: String) : AuthState()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        println("=== Generated Swift Code ===")
        println(result.swiftCode)
        println("=== End ===")

        assertTrue(result.declarationsTransformed >= 2, "Should transform at least 2 sealed classes")

        // Check NetworkResult enum
        assertContains(result.swiftCode, "enum NetworkResult")
        assertContains(result.swiftCode, "case success")
        assertContains(result.swiftCode, "case error")
        assertContains(result.swiftCode, "case loading")

        // Check AuthState enum
        assertContains(result.swiftCode, "enum AuthState")
        assertContains(result.swiftCode, "case loggedOut")
        assertContains(result.swiftCode, "case loggedIn")
    }

    @Test
    fun `combined file generates valid Swift`() {
        val kotlinSource1 =
            """
            @SwiftEnum
            sealed class Result<out T> {
                data class Success<T>(val value: T) : Result<T>()
                data class Failure(val error: String) : Result<Nothing>()
            }
            """.trimIndent()

        val kotlinSource2 =
            """
            suspend fun fetchData(id: Int, limit: Int = 100): String {
                return "data"
            }
            """.trimIndent()

        val result1 = transformer.transform(kotlinSource1, dslConfig)
        val result2 = transformer.transform(kotlinSource2, dslConfig)

        // Generate combined Swift file
        val combinedSwift =
            buildString {
                appendLine("// Generated by Swiftify")
                appendLine("import Foundation")
                appendLine()
                appendLine("// MARK: - Result")
                appendLine(result1.swiftCode)
                appendLine()
                appendLine("// MARK: - Functions")
                appendLine(result2.swiftCode)
            }

        println("=== Combined Swift File ===")
        println(combinedSwift)
        println("=== End ===")

        assertContains(combinedSwift, "enum Result")
        assertContains(combinedSwift, "func fetchData")
        assertContains(combinedSwift, "import Foundation")
    }
}
