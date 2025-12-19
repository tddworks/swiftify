package io.swiftify.generator

import io.swiftify.dsl.swiftify
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for the complete transformation pipeline.
 * Analyzer → Transformer → Generator
 */
class SwiftifyTransformerTest {
    private val transformer = SwiftifyTransformer()

    @Test
    fun `transform sealed class to swift enum`() {
        val kotlinSource =
            """
            package com.example

            sealed class NetworkResult {
                data class Success(val data: String) : NetworkResult()
                data class Failure(val error: Throwable) : NetworkResult()
                object Loading : NetworkResult()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum NetworkResult")
        assertContains(result.swiftCode, "case success(data: String)")
        assertContains(result.swiftCode, "case failure(error: Error)")
        assertContains(result.swiftCode, "case loading")
    }

    @Test
    fun `transform suspend function to async with annotation`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun fetchUser(id: Int): User
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // Kotlin Int maps to Swift Int32 in Kotlin/Native
        assertContains(result.swiftCode, "public func fetchUser(id: Int32) async throws -> User")
    }

    @Test
    fun `transform suspend function with DSL mode`() {
        val kotlinSource =
            """
            package com.example

            suspend fun fetchUser(id: Int): User
            """.trimIndent()

        // DSL mode - process all functions without annotations
        val config =
            swiftify {
                defaults {
                    requireAnnotations = false
                }
            }
        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "public func fetchUser(id: Int32) async throws -> User")
    }

    @Test
    fun `transform with custom configuration`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
                data class Loading(val progress: Float) : State()
            }
            """.trimIndent()

        val config =
            swiftify {
                sealedClasses {
                    transformToEnum(exhaustive = true)
                    conformTo("Hashable")
                }
            }

        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "@frozen")
        assertContains(result.swiftCode, "Hashable")
    }

    @Test
    fun `transform annotated class with custom name`() {
        val kotlinSource =
            """
            package com.example

            @SwiftEnum(name = "AppResult", exhaustive = true)
            sealed class Result {
                data class Success(val value: String) : Result()
                object Failure : Result()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum AppResult")
    }

    @Test
    fun `transform multiple declarations with DSL mode`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
                object Loading : State()
            }

            suspend fun loadState(): State

            fun observeState(): Flow<State>
            """.trimIndent()

        // DSL mode - process all functions without annotations
        val config =
            swiftify {
                defaults {
                    requireAnnotations = false
                }
            }
        val result = transformer.transform(kotlinSource, config)

        // Should generate enum
        assertContains(result.swiftCode, "public enum State")

        // Should generate async function
        assertContains(result.swiftCode, "func loadState() async")
    }

    @Test
    fun `result contains declaration count`() {
        val kotlinSource =
            """
            package com.example

            sealed class A { object X : A() }
            sealed class B { object Y : B() }
            @SwiftDefaults
            suspend fun foo(): String
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed >= 3)
    }

    @Test
    fun `transform generic sealed class`() {
        val kotlinSource =
            """
            package com.example

            sealed class Result<T> {
                data class Success<T>(val value: T) : Result<T>()
                data class Error(val message: String) : Result<Nothing>()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum Result<T>")
        assertContains(result.swiftCode, "case success(value: T)")
    }
}
