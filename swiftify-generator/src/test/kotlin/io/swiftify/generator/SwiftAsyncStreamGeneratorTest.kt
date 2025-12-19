package io.swiftify.generator

import io.swiftify.swift.SwiftAsyncStreamSpec
import io.swiftify.swift.SwiftParameter
import io.swiftify.swift.SwiftType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift AsyncSequence code generation.
 */
class SwiftAsyncStreamGeneratorTest {
    private val generator = SwiftAsyncStreamGenerator()

    @Test
    fun `generates simple async sequence function`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeUpdates",
                parameters = emptyList(),
                elementType = SwiftType.Named("Update"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observeUpdates()"))
        assertTrue(result.contains("AsyncStream<Update>"))
    }

    @Test
    fun `generates async sequence with parameters`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeUser",
                parameters =
                listOf(
                    SwiftParameter(name = "userId", type = SwiftType.Named("String")),
                ),
                elementType = SwiftType.Named("UserState"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observeUser(userId: String)"))
        assertTrue(result.contains("AsyncStream<UserState>"))
    }

    @Test
    fun `generates async sequence property`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "updates",
                parameters = emptyList(),
                elementType = SwiftType.Named("Update"),
                isProperty = true,
            )

        val result = generator.generate(spec)

        // Property name gets "Stream" suffix to avoid collision with Kotlin property
        assertTrue(result.contains("var updatesStream: AsyncStream<Update>"))
    }

    @Test
    fun `generates async throwing sequence for nullable elements`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeData",
                parameters = emptyList(),
                elementType = SwiftType.Optional(SwiftType.Named("Data")),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<Data?>"))
    }

    @Test
    fun `generates generic async sequence`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observe",
                typeParameters = listOf("T"),
                parameters = emptyList(),
                elementType = SwiftType.Generic("T"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observe<T>()"))
        assertTrue(result.contains("AsyncStream<T>"))
    }

    @Test
    fun `generates shared async sequence for StateFlow`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "state",
                parameters = emptyList(),
                elementType = SwiftType.Named("AppState"),
                isProperty = true,
                isShared = true,
                replayCount = 1,
            )

        val result = generator.generate(spec)

        // StateFlow generates a property that can be observed
        // Property name gets "Stream" suffix to avoid collision with Kotlin property
        assertTrue(result.contains("var stateStream:"))
        assertTrue(result.contains("AppState"))
    }

    @Test
    fun `generates internal access level`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "internalStream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
                accessLevel = SwiftAsyncStreamSpec.AccessLevel.INTERNAL,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("internal func"))
    }

    @Test
    fun `generates async sequence with array element type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeItems",
                parameters = emptyList(),
                elementType = SwiftType.Array(SwiftType.Named("Item")),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<[Item]>"))
    }

    @Test
    fun `generateFunctionBody adds Stream suffix to property name`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "currentUser",
                parameters = emptyList(),
                elementType = SwiftType.Named("User"),
                isProperty = true,
            )

        val result = generator.generateFunctionBody(spec)
        println("Generated body:\n$result")

        // Property name must have "Stream" suffix to avoid collision with Kotlin property
        assertTrue(result.contains("var currentUserStream:"), "Expected 'currentUserStream' but got:\n$result")
        // The internal call should use the original name to access the Kotlin property
        assertTrue(result.contains("self.currentUser.collect"), "Expected 'self.currentUser.collect' but got:\n$result")
    }
}
