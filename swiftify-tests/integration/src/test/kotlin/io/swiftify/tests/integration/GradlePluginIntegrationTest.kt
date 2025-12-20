package io.swiftify.tests.integration

import io.swiftify.dsl.swiftify
import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the Swiftify transformation pipeline.
 *
 * NOTE: Swiftify generates convenience code for:
 * - Functions WITH default parameters (generates convenience overloads)
 * - Flow functions (generates AsyncStream wrappers)
 * - Sealed classes WITH @SwiftEnum annotation (generates Swift enums)
 *
 * Functions WITHOUT default parameters don't need transformation (Kotlin 2.0+ handles them).
 * Sealed classes WITHOUT @SwiftEnum don't need transformation (Kotlin/Native exports them).
 */
class SwiftifyTransformationIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private val transformer = SwiftifyTransformer()

    // DSL mode config - process all functions without annotations
    private val dslConfig = swiftify {
        defaults {
            requireAnnotations = false
        }
    }

    @Test
    fun `transforms complete sealed class hierarchy with SwiftEnum`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class NetworkResult<T> {
                data class Success<T>(val data: T, val statusCode: Int) : NetworkResult<T>()
                data class Failure(val error: Throwable, val statusCode: Int) : NetworkResult<Nothing>()
                object Loading : NetworkResult<Nothing>()
                object Idle : NetworkResult<Nothing>()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertContains(result.swiftCode, "public enum NetworkResult<T>")
        // Kotlin Int maps to Swift Int32 in Kotlin/Native
        assertContains(result.swiftCode, "case success(data: T, statusCode: Int32)")
        assertContains(result.swiftCode, "case failure(error: Error, statusCode: Int32)")
        assertContains(result.swiftCode, "case loading")
        assertContains(result.swiftCode, "case idle")
    }

    @Test
    fun `transforms suspend functions with defaults`() {
        val kotlinSource = """
            package com.example

            suspend fun fetchUser(id: Int, limit: Int = 10): User

            suspend fun fetchUsers(ids: List<Int>, pageSize: Int = 20): List<User>

            suspend fun deleteUser(id: Int, force: Boolean = false): Unit
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // Should generate convenience overloads for functions with defaults
        assertTrue(result.declarationsTransformed >= 3)
        assertContains(result.swiftCode, "func fetchUser")
        assertContains(result.swiftCode, "func fetchUsers")
        assertContains(result.swiftCode, "func deleteUser")
    }

    @Test
    fun `transforms with custom configuration`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class State {
                object Idle : State()
                data class Active(val data: String) : State()
            }
        """.trimIndent()

        val config = swiftify {
            defaults {
                requireAnnotations = false
            }
            sealedClasses {
                transformToEnum(exhaustive = true)
                conformTo("Hashable", "Codable")
            }
        }

        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "@frozen")
        assertContains(result.swiftCode, "Hashable")
        assertContains(result.swiftCode, "Codable")
    }

    @Test
    fun `handles complex nested types`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class Response {
                data class Success(val items: List<Item>, val metadata: Map<String, Any>) : Response()
                data class Error(val errors: List<ErrorInfo>) : Response()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertContains(result.swiftCode, "public enum Response")
        assertContains(result.swiftCode, "case success")
        assertContains(result.swiftCode, "case error")
    }

    @Test
    fun `transforms file with mixed declarations`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class LoadingState {
                object NotStarted : LoadingState()
                object InProgress : LoadingState()
                data class Completed(val result: String) : LoadingState()
                data class Failed(val error: Throwable) : LoadingState()
            }

            suspend fun loadData(id: Int = 0): LoadingState

            suspend fun refreshData(force: Boolean = false): LoadingState
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // Should have enum
        assertContains(result.swiftCode, "public enum LoadingState")
        assertContains(result.swiftCode, "case notStarted")
        assertContains(result.swiftCode, "case inProgress")
        assertContains(result.swiftCode, "case completed")
        assertContains(result.swiftCode, "case failed")

        // Should have convenience overloads for functions with defaults
        assertContains(result.swiftCode, "func loadData")
        assertContains(result.swiftCode, "func refreshData")
    }

    @Test
    fun `writes output to file`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class State {
                object Idle : State()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        val outputFile = File(tempDir, "State.swift")
        outputFile.writeText(result.swiftCode)

        assertTrue(outputFile.exists())
        assertContains(outputFile.readText(), "public enum State")
    }

    @Test
    fun `processes multiple source files`() {
        val sources = listOf(
            """
                package com.example

                @SwiftEnum
                sealed class Result {
                    data class Success(val value: String) : Result()
                    object Failure : Result()
                }
            """.trimIndent(),
            """
                package com.example

                suspend fun process(limit: Int = 10): Result
            """.trimIndent(),
        )

        val results = sources.map { transformer.transform(it, dslConfig) }

        assertTrue(results[0].swiftCode.contains("enum Result"))
        assertTrue(results[1].swiftCode.contains("func process"))
    }

    @Test
    fun `transforms Flow to AsyncStream`() {
        val kotlinSource = """
            package com.example

            fun observeUpdates(): Flow<Update>

            fun watchState(userId: String): Flow<UserState>
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        assertContains(result.swiftCode, "observeUpdates")
        assertContains(result.swiftCode, "AsyncStream<Update>")
        assertContains(result.swiftCode, "watchState")
        assertContains(result.swiftCode, "AsyncStream<UserState>")
    }

    @Test
    fun `transforms all declaration types together`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum
            sealed class AppState {
                object Loading : AppState()
                data class Loaded(val data: String) : AppState()
                data class Error(val message: String) : AppState()
            }

            suspend fun loadData(limit: Int = 10): AppState

            fun observeState(): Flow<AppState>
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // Enum
        assertContains(result.swiftCode, "public enum AppState")
        assertContains(result.swiftCode, "case loading")
        assertContains(result.swiftCode, "case loaded")
        assertContains(result.swiftCode, "case error")

        // Convenience overload for function with defaults
        assertContains(result.swiftCode, "func loadData")

        // AsyncStream
        assertContains(result.swiftCode, "observeState")
        assertContains(result.swiftCode, "AsyncStream<AppState>")
    }

    @Test
    fun `sealed class without SwiftEnum is not transformed`() {
        val kotlinSource = """
            package com.example

            sealed class State {
                object Idle : State()
                object Active : State()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // Without @SwiftEnum, sealed classes are not transformed
        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `suspend function without defaults is not transformed`() {
        val kotlinSource = """
            package com.example

            suspend fun fetchData(id: Int): String
        """.trimIndent()

        val result = transformer.transform(kotlinSource, dslConfig)

        // No default parameters, so no transformation needed
        assertEquals(0, result.declarationsTransformed)
    }
}
