package io.swiftify.tests.unit

import io.swiftify.common.SwiftAsyncSequenceSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift AsyncSequence specifications.
 * These represent Kotlin Flow transformed to Swift AsyncSequence.
 */
class SwiftAsyncSequenceSpecTest {

    @Test
    fun `create simple async sequence spec`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeUpdates",
            parameters = emptyList(),
            elementType = SwiftType.Named("Update")
        )

        assertEquals("observeUpdates", spec.name)
        assertTrue(spec.parameters.isEmpty())
        assertEquals("Update", (spec.elementType as SwiftType.Named).name)
    }

    @Test
    fun `async sequence with parameters`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeUser",
            parameters = listOf(
                SwiftParameter(name = "userId", type = SwiftType.Named("String"))
            ),
            elementType = SwiftType.Named("UserState")
        )

        assertEquals(1, spec.parameters.size)
        assertEquals("userId", spec.parameters[0].name)
    }

    @Test
    fun `async sequence with optional element type`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeNullable",
            parameters = emptyList(),
            elementType = SwiftType.Optional(SwiftType.Named("Data"))
        )

        assertTrue(spec.elementType is SwiftType.Optional)
    }

    @Test
    fun `async sequence with generic element type`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observe",
            typeParameters = listOf("T"),
            parameters = emptyList(),
            elementType = SwiftType.Generic("T")
        )

        assertEquals(listOf("T"), spec.typeParameters)
        assertTrue(spec.elementType is SwiftType.Generic)
    }

    @Test
    fun `async sequence as property`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "updates",
            parameters = emptyList(),
            elementType = SwiftType.Named("Update"),
            isProperty = true
        )

        assertTrue(spec.isProperty)
    }

    @Test
    fun `async sequence with complex element type`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "observeResults",
            parameters = emptyList(),
            elementType = SwiftType.Parameterized(
                base = "Result",
                arguments = listOf(
                    SwiftType.Named("Data"),
                    SwiftType.Named("Error")
                )
            )
        )

        assertTrue(spec.elementType is SwiftType.Parameterized)
    }

    @Test
    fun `async sequence from StateFlow (shared)`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "state",
            parameters = emptyList(),
            elementType = SwiftType.Named("AppState"),
            isShared = true,
            replayCount = 1
        )

        assertTrue(spec.isShared)
        assertEquals(1, spec.replayCount)
    }

    @Test
    fun `async sequence from SharedFlow with replay`() {
        val spec = SwiftAsyncSequenceSpec(
            name = "events",
            parameters = emptyList(),
            elementType = SwiftType.Named("Event"),
            isShared = true,
            replayCount = 5
        )

        assertEquals(5, spec.replayCount)
    }
}
