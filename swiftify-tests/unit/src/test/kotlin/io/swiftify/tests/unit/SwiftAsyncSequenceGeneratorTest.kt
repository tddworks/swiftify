package io.swiftify.tests.unit

import io.swiftify.common.SwiftAsyncSequenceSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import io.swiftify.generator.SwiftAsyncSequenceGenerator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift AsyncSequence code generation.
 */
class SwiftAsyncSequenceGeneratorTest {

    private val generator = SwiftAsyncSequenceGenerator()

    @Test
    fun `generates simple async sequence function`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeUpdates",
            parameters = emptyList(),
            elementType = SwiftType.Named("Update")
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observeUpdates()"))
        assertTrue(result.contains("AsyncStream<Update>"))
    }

    @Test
    fun `generates async sequence with parameters`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeUser",
            parameters = listOf(
                SwiftParameter(name = "userId", type = SwiftType.Named("String"))
            ),
            elementType = SwiftType.Named("UserState")
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observeUser(userId: String)"))
        assertTrue(result.contains("AsyncStream<UserState>"))
    }

    @Test
    fun `generates async sequence property`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "updates",
            parameters = emptyList(),
            elementType = SwiftType.Named("Update"),
            isProperty = true
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("var updates: AsyncStream<Update>"))
    }

    @Test
    fun `generates async throwing sequence for nullable elements`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeData",
            parameters = emptyList(),
            elementType = SwiftType.Optional(SwiftType.Named("Data"))
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<Data?>"))
    }

    @Test
    fun `generates generic async sequence`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observe",
            typeParameters = listOf("T"),
            parameters = emptyList(),
            elementType = SwiftType.Generic("T")
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observe<T>()"))
        assertTrue(result.contains("AsyncStream<T>"))
    }

    @Test
    fun `generates shared async sequence for StateFlow`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "state",
            parameters = emptyList(),
            elementType = SwiftType.Named("AppState"),
            isProperty = true,
            isShared = true,
            replayCount = 1
        )

        val result = generator.generate(spec)

        // StateFlow generates a property that can be observed
        assertTrue(result.contains("var state:"))
        assertTrue(result.contains("AppState"))
    }

    @Test
    fun `generates internal access level`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "internalStream",
            parameters = emptyList(),
            elementType = SwiftType.Named("Data"),
            accessLevel = SwiftAsyncSequenceSpec.AccessLevel.INTERNAL
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("internal func"))
    }

    @Test
    fun `generates async sequence with array element type`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeItems",
            parameters = emptyList(),
            elementType = SwiftType.Array(SwiftType.Named("Item"))
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<[Item]>"))
    }
}
