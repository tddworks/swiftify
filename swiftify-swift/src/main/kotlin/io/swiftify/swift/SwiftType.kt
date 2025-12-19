package io.swiftify.swift

/**
 * Represents Swift types in the Swiftify type system.
 * This is a user-facing model that represents desired Swift output.
 */
sealed class SwiftType {

    /**
     * The Swift source code representation of this type.
     */
    abstract val swiftRepresentation: String

    /**
     * A simple named type like String, Int, Data, Error, etc.
     */
    data class Named(val name: String) : SwiftType() {
        override val swiftRepresentation: String get() = name
    }

    /**
     * A generic type parameter like T, E, Element, etc.
     */
    data class Generic(val name: String) : SwiftType() {
        override val swiftRepresentation: String get() = name
    }

    /**
     * An optional type (T?)
     */
    data class Optional(val wrapped: SwiftType) : SwiftType() {
        override val swiftRepresentation: String get() = "${wrapped.swiftRepresentation}?"
    }

    /**
     * An array type ([T])
     */
    data class Array(val elementType: SwiftType) : SwiftType() {
        override val swiftRepresentation: String get() = "[${elementType.swiftRepresentation}]"
    }

    /**
     * A dictionary type ([K: V])
     */
    data class Dictionary(val keyType: SwiftType, val valueType: SwiftType) : SwiftType() {
        override val swiftRepresentation: String
            get() = "[${keyType.swiftRepresentation}: ${valueType.swiftRepresentation}]"
    }

    /**
     * A parameterized generic type like Result<T, E> or Array<Int>
     */
    data class Parameterized(val base: String, val arguments: List<SwiftType>) : SwiftType() {
        override val swiftRepresentation: String
            get() = "$base<${arguments.joinToString(", ") { it.swiftRepresentation }}>"
    }

    /**
     * A function/closure type like (Int, String) -> Bool
     */
    data class Function(
        val parameters: List<SwiftType>,
        val returnType: SwiftType,
        val isAsync: Boolean = false,
        val isThrowing: Boolean = false
    ) : SwiftType() {
        override val swiftRepresentation: String
            get() {
                val params = "(${parameters.joinToString(", ") { it.swiftRepresentation }})"
                val modifiers = buildString {
                    if (isAsync) append(" async")
                    if (isThrowing) append(" throws")
                }
                return "$params$modifiers -> ${returnType.swiftRepresentation}"
            }
    }

    /**
     * A tuple type like (String, Int)
     */
    data class Tuple(val elements: List<SwiftType>) : SwiftType() {
        override val swiftRepresentation: String
            get() = "(${elements.joinToString(", ") { it.swiftRepresentation }})"
    }

    /**
     * The Void type
     */
    data object Void : SwiftType() {
        override val swiftRepresentation: String get() = "Void"
    }
}
