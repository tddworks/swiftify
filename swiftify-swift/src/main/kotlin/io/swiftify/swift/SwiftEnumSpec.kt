package io.swiftify.swift

/**
 * Specification for a Swift enum to be generated from Kotlin sealed class.
 * This represents the user's desired Swift output.
 */
data class SwiftEnumSpec(
    /**
     * The name of the Swift enum.
     */
    val name: String,
    /**
     * The enum cases.
     */
    val cases: List<SwiftEnumCase>,
    /**
     * Type parameters for generic enums (e.g., ["T", "E"] for Result<T, E>).
     */
    val typeParameters: List<String> = emptyList(),
    /**
     * Protocol conformances (e.g., ["Hashable", "Codable"]).
     */
    val conformances: List<String> = emptyList(),
    /**
     * Whether this enum is exhaustive (all cases covered, no 'else' needed).
     * When true, Swift compiler will enforce exhaustive switch statements.
     */
    val isExhaustive: Boolean = false,
    /**
     * Access level for the generated enum.
     */
    val accessLevel: AccessLevel = AccessLevel.PUBLIC,
) {
    enum class AccessLevel {
        PUBLIC,
        INTERNAL,
        PRIVATE,
    }
}

/**
 * Represents a single case in a Swift enum.
 */
data class SwiftEnumCase(
    /**
     * The name of the case (e.g., "success", "failure").
     */
    val name: String,
    /**
     * Associated values for this case.
     */
    val associatedValues: List<AssociatedValue> = emptyList(),
) {
    /**
     * An associated value attached to an enum case.
     */
    data class AssociatedValue(
        /**
         * The label for this associated value (e.g., "value" in `.success(value: T)`).
         */
        val label: String,
        /**
         * The type of the associated value.
         */
        val type: SwiftType,
    )
}
