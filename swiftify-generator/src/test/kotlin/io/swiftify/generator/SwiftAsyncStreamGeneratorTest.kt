package io.swiftify.generator

import io.swiftify.swift.SwiftAsyncStreamSpec
import io.swiftify.swift.SwiftParameter
import io.swiftify.swift.SwiftType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        // Property name must have "Stream" suffix to avoid collision with Kotlin property
        assertTrue(result.contains("var currentUserStream:"), "Expected 'currentUserStream' but got:\n$result")
        // The internal call should use the original name to access the Kotlin property
        assertTrue(result.contains("self.currentUser.collect"), "Expected 'self.currentUser.collect' but got:\n$result")
    }

    // ============================================================
    // Implementation Generation Tests
    // ============================================================

    @Test
    fun `generateWithImplementation wraps in extension for class`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeData",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
            )

        val result = generator.generateWithImplementation(spec, "Repository")

        assertTrue(result.contains("extension Repository"))
    }

    @Test
    fun `generateWithImplementation includes AsyncStream creation`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "watchChanges",
                parameters = emptyList(),
                elementType = SwiftType.Named("Change"),
            )

        val result = generator.generateWithImplementation(spec, "ChangeTracker")

        assertTrue(result.contains("AsyncStream { continuation in"))
    }

    @Test
    fun `generateWithImplementation uses SwiftifyFlowCollector`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "stream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Item"),
            )

        val result = generator.generateWithImplementation(spec, "ItemStore")

        assertTrue(result.contains("SwiftifyFlowCollector"))
        assertTrue(result.contains("onEmit:"))
        assertTrue(result.contains("onComplete:"))
        assertTrue(result.contains("onError:"))
    }

    @Test
    fun `generateWithImplementation calls collect on flow`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "events",
                parameters = emptyList(),
                elementType = SwiftType.Named("Event"),
            )

        val result = generator.generateWithImplementation(spec, "EventBus")

        assertTrue(result.contains(".collect(collector:"))
    }

    @Test
    fun `generateWithImplementation for property uses self prefix`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "state",
                parameters = emptyList(),
                elementType = SwiftType.Named("State"),
                isProperty = true,
            )

        val result = generator.generateWithImplementation(spec, "StateHolder")

        assertTrue(result.contains("self.state.collect"))
    }

    @Test
    fun `generateWithImplementation without class omits extension`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "globalStream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Value"),
            )

        val result = generator.generateWithImplementation(spec, null)

        assertFalse(result.contains("extension"))
    }

    // ============================================================
    // Function Body Tests
    // ============================================================

    @Test
    fun `generateFunctionBody for function includes parameter forwarding`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "watchUser",
                parameters = listOf(
                    SwiftParameter(name = "userId", type = SwiftType.Named("String")),
                ),
                elementType = SwiftType.Named("UserState"),
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("userId: userId"))
    }

    @Test
    fun `generateFunctionBody for function includes all parameters`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "watchItems",
                parameters = listOf(
                    SwiftParameter(name = "category", type = SwiftType.Named("String")),
                    SwiftParameter(name = "limit", type = SwiftType.Named("Int")),
                ),
                elementType = SwiftType.Named("Item"),
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("category: category"))
        assertTrue(result.contains("limit: limit"))
    }

    @Test
    fun `generateFunctionBody includes continuation yield`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "stream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("continuation.yield(value)"))
    }

    @Test
    fun `generateFunctionBody includes continuation finish`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "stream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("continuation.finish()"))
    }

    // ============================================================
    // Type Handling Tests
    // ============================================================

    @Test
    fun `generates async stream with dictionary element type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeMap",
                parameters = emptyList(),
                elementType = SwiftType.Dictionary(
                    SwiftType.Named("String"),
                    SwiftType.Named("Int"),
                ),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<[String: Int]>"))
    }

    @Test
    fun `generates async stream with nested optional type`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observeOptional",
                parameters = emptyList(),
                elementType = SwiftType.Optional(
                    SwiftType.Array(SwiftType.Named("Item")),
                ),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("AsyncStream<[Item]?>"))
    }

    @Test
    fun `generates async stream with multiple type parameters`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observe",
                typeParameters = listOf("T", "E"),
                parameters = emptyList(),
                elementType = SwiftType.Generic("T"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("func observe<T, E>()"))
    }

    // ============================================================
    // Access Level Tests
    // ============================================================

    @Test
    fun `generates private async stream`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "privateStream",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
                accessLevel = SwiftAsyncStreamSpec.AccessLevel.PRIVATE,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("private func"))
    }

    @Test
    fun `generates private async stream property`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "internalState",
                parameters = emptyList(),
                elementType = SwiftType.Named("State"),
                isProperty = true,
                accessLevel = SwiftAsyncStreamSpec.AccessLevel.PRIVATE,
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("private var"))
    }

    // ============================================================
    // Validation Tests
    // ============================================================

    @Test
    fun `generate throws for blank name`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "",
                parameters = emptyList(),
                elementType = SwiftType.Named("Data"),
            )

        val exception = org.junit.jupiter.api.assertThrows<io.swiftify.swift.SwiftifyValidationException> {
            generator.generate(spec)
        }

        assertTrue(exception.message?.contains("name") == true)
    }

    @Test
    fun `generate throws for blank parameter name`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "validStream",
                parameters = listOf(
                    SwiftParameter(name = "", type = SwiftType.Named("String")),
                ),
                elementType = SwiftType.Named("Data"),
            )

        val exception = org.junit.jupiter.api.assertThrows<io.swiftify.swift.SwiftifyValidationException> {
            generator.generate(spec)
        }

        assertTrue(exception.message?.contains("Parameter") == true)
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun `generates async stream with default parameter value`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "watchWithLimit",
                parameters = listOf(
                    SwiftParameter(
                        name = "limit",
                        type = SwiftType.Named("Int"),
                        defaultValue = "100",
                    ),
                ),
                elementType = SwiftType.Named("Item"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("limit: Int = 100"))
    }

    @Test
    fun `generates async stream with external parameter name`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observe",
                parameters = listOf(
                    SwiftParameter(
                        name = "userId",
                        externalName = "for",
                        type = SwiftType.Named("String"),
                    ),
                ),
                elementType = SwiftType.Named("UserEvent"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("for userId: String"))
    }

    @Test
    fun `generates async stream with no argument label`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "observe",
                parameters = listOf(
                    SwiftParameter(
                        name = "id",
                        externalName = "_",
                        type = SwiftType.Named("String"),
                    ),
                ),
                elementType = SwiftType.Named("Event"),
            )

        val result = generator.generate(spec)

        assertTrue(result.contains("_ id: String"))
    }

    @Test
    fun `generateFunctionBody handles empty parameters for property`() {
        val spec =
            SwiftAsyncStreamSpec(
                name = "items",
                parameters = emptyList(),
                elementType = SwiftType.Named("Item"),
                isProperty = true,
            )

        val result = generator.generateFunctionBody(spec)

        assertTrue(result.contains("var itemsStream"))
        // Properties have computed property syntax: var name: Type { ... }
        assertTrue(result.contains("AsyncStream<Item> {"))
    }
}
