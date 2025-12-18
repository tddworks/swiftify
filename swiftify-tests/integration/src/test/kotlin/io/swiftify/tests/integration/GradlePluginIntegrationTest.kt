package io.swiftify.tests.integration

import io.swiftify.generator.SwiftifyTransformer
import io.swiftify.dsl.swiftify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * Integration tests for the Swiftify transformation pipeline.
 */
class SwiftifyTransformationIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private val transformer = SwiftifyTransformer()

    @Test
    fun `transforms complete sealed class hierarchy`() {
        val kotlinSource = """
            package com.example

            sealed class NetworkResult<T> {
                data class Success<T>(val data: T, val statusCode: Int) : NetworkResult<T>()
                data class Failure(val error: Throwable, val statusCode: Int) : NetworkResult<Nothing>()
                object Loading : NetworkResult<Nothing>()
                object Idle : NetworkResult<Nothing>()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum NetworkResult<T>")
        assertContains(result.swiftCode, "case success(data: T, statusCode: Int)")
        assertContains(result.swiftCode, "case failure(error: Error, statusCode: Int)")
        assertContains(result.swiftCode, "case loading")
        assertContains(result.swiftCode, "case idle")
    }

    @Test
    fun `transforms multiple suspend functions`() {
        val kotlinSource = """
            package com.example

            suspend fun fetchUser(id: Int): User

            suspend fun fetchUsers(ids: List<Int>): List<User>

            suspend fun deleteUser(id: Int): Unit
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "func fetchUser(id: Int) async throws -> User")
        assertContains(result.swiftCode, "func fetchUsers(ids: [Int]) async throws -> [User]")
        // Note: Unit returns become void (no return type)
    }

    @Test
    fun `transforms with custom configuration`() {
        val kotlinSource = """
            package com.example

            sealed class State {
                object Idle : State()
                data class Active(val data: String) : State()
            }
        """.trimIndent()

        val config = swiftify {
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

            sealed class Response {
                data class Success(val items: List<Item>, val metadata: Map<String, Any>) : Response()
                data class Error(val errors: List<ErrorInfo>) : Response()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum Response")
        assertContains(result.swiftCode, "case success")
        assertContains(result.swiftCode, "case error")
    }

    @Test
    fun `transforms file with mixed declarations`() {
        val kotlinSource = """
            package com.example

            sealed class LoadingState {
                object NotStarted : LoadingState()
                object InProgress : LoadingState()
                data class Completed(val result: String) : LoadingState()
                data class Failed(val error: Throwable) : LoadingState()
            }

            suspend fun loadData(): LoadingState

            suspend fun refreshData(force: Boolean = false): LoadingState
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // Should have enum
        assertContains(result.swiftCode, "public enum LoadingState")
        assertContains(result.swiftCode, "case notStarted")
        assertContains(result.swiftCode, "case inProgress")
        assertContains(result.swiftCode, "case completed")
        assertContains(result.swiftCode, "case failed")

        // Should have async functions
        assertContains(result.swiftCode, "func loadData() async throws -> LoadingState")
        assertContains(result.swiftCode, "func refreshData(force: Bool")
    }

    @Test
    fun `writes output to file`() {
        val kotlinSource = """
            package com.example

            sealed class State {
                object Idle : State()
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

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

                sealed class Result {
                    data class Success(val value: String) : Result()
                    object Failure : Result()
                }
            """.trimIndent(),
            """
                package com.example

                suspend fun process(): Result
            """.trimIndent()
        )

        val results = sources.map { transformer.transform(it) }

        assertTrue(results[0].swiftCode.contains("enum Result"))
        assertTrue(results[1].swiftCode.contains("func process"))
    }
}
