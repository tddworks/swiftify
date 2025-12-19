package io.swiftify.swift

/**
 * Specification for generating Swift convenience overloads.
 * Represents functions with default parameters that need overloads generated
 * since Swift doesn't support default parameters from Kotlin/Objective-C.
 */
data class SwiftDefaultsSpec(
    /**
     * The function name.
     */
    val name: String,

    /**
     * Function parameters.
     */
    val parameters: List<SwiftParameter>,

    /**
     * The return type of the function.
     */
    val returnType: SwiftType,

    /**
     * Type parameters for generic functions.
     */
    val typeParameters: List<String> = emptyList(),

    /**
     * Whether this function can throw errors.
     */
    val isThrowing: Boolean = false,

    /**
     * The receiver type if this is a method on a type (extension).
     */
    val receiverType: String? = null,

    /**
     * Access level for the generated function.
     */
    val accessLevel: AccessLevel = AccessLevel.PUBLIC
) {
    /**
     * Async functions are always async by definition.
     */
    val isAsync: Boolean = true

    enum class AccessLevel {
        PUBLIC,
        INTERNAL,
        PRIVATE
    }
}

/**
 * Represents a Swift function parameter.
 */
data class SwiftParameter(
    /**
     * The internal parameter name used in the function body.
     */
    val name: String,

    /**
     * The type of the parameter.
     */
    val type: SwiftType,

    /**
     * The external parameter name (argument label).
     * If null, uses the internal name. Use "_" for no label.
     */
    val externalName: String? = null,

    /**
     * Default value for the parameter (as Swift source string).
     */
    val defaultValue: String? = null,

    /**
     * Whether this is a variadic parameter.
     */
    val isVariadic: Boolean = false,

    /**
     * Whether this parameter is inout.
     */
    val isInout: Boolean = false
)
