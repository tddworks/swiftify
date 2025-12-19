package io.swiftify.swift

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for SwiftAsyncStreamSpec - specifications for transforming
 * Kotlin Flow to Swift AsyncStream for native for-await syntax.
 */
class SwiftAsyncStreamSpecTest {
    @Test
    fun `create simple async sequence spec`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeUpdates",
                parameters = emptyList(),
                elementType = SwiftType.Named("Update"),
            )

        assertEquals("observeUpdates", spec.name)
        assertTrue(spec.parameters.isEmpty())
        assertEquals("Update", (spec.elementType as SwiftType.Named).name)
    }

    @Test
    fun `async sequence with parameters`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeUser",
                parameters =
                listOf(
                    SwiftParameter(name = "userId", type = SwiftType.Named("String")),
                ),
                elementType = SwiftType.Named("UserState"),
            )

        assertEquals(1, spec.parameters.size)
        assertEquals("userId", spec.parameters[0].name)
    }

    @Test
    fun `async sequence with optional element type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeNullable",
                parameters = emptyList(),
                elementType = SwiftType.Optional(SwiftType.Named("Data")),
            )

        assertTrue(spec.elementType is SwiftType.Optional)
    }

    @Test
    fun `async sequence with generic element type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observe",
                typeParameters = listOf("T"),
                parameters = emptyList(),
                elementType = SwiftType.Generic("T"),
            )

        assertEquals(listOf("T"), spec.typeParameters)
        assertTrue(spec.elementType is SwiftType.Generic)
    }

    @Test
    fun `async sequence as property`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "updates",
                parameters = emptyList(),
                elementType = SwiftType.Named("Update"),
                isProperty = true,
            )

        assertTrue(spec.isProperty)
    }

    @Test
    fun `async sequence with complex element type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeResults",
                parameters = emptyList(),
                elementType =
                SwiftType.Parameterized(
                    base = "Result",
                    arguments =
                    listOf(
                        SwiftType.Named("Data"),
                        SwiftType.Named("Error"),
                    ),
                ),
            )

        assertTrue(spec.elementType is SwiftType.Parameterized)
    }

    @Test
    fun `async sequence from StateFlow (shared)`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "state",
                parameters = emptyList(),
                elementType = SwiftType.Named("AppState"),
                isShared = true,
                replayCount = 1,
            )

        assertTrue(spec.isShared)
        assertEquals(1, spec.replayCount)
    }

    @Test
    fun `async sequence from SharedFlow with replay`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "events",
                parameters = emptyList(),
                elementType = SwiftType.Named("Event"),
                isShared = true,
                replayCount = 5,
            )

        assertEquals(5, spec.replayCount)
    }
}
