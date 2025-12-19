package io.swiftify.swift

/**
 * Specification for a Swift AsyncStream.
 * Represents Kotlin Flow transformed to Swift AsyncStream for native for-await syntax.
 */
data class SwiftAsyncStreamSpec(
    /**
     * The function or property name.
     */
    val name: String,
    /**
     * Function parameters (empty for properties).
     */
    val parameters: List<SwiftParameter>,
    /**
     * The element type of the sequence.
     */
    val elementType: SwiftType,
    /**
     * Type parameters for generic sequences.
     */
    val typeParameters: List<String> = emptyList(),
    /**
     * Whether this is a property (var/let) rather than a function.
     */
    val isProperty: Boolean = false,
    /**
     * Whether this is from a SharedFlow/StateFlow (hot flow).
     * Affects the Swift implementation strategy.
     */
    val isShared: Boolean = false,
    /**
     * Replay count for SharedFlow (0 for regular Flow, 1 for StateFlow).
     */
    val replayCount: Int = 0,
    /**
     * Access level for the generated declaration.
     */
    val accessLevel: AccessLevel = AccessLevel.PUBLIC,
) {
    enum class AccessLevel {
        PUBLIC,
        INTERNAL,
        PRIVATE,
    }
}
