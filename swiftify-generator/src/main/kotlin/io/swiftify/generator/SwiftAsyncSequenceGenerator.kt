package io.swiftify.generator

import io.swiftify.common.SwiftAsyncSequenceSpec
import io.swiftify.common.SwiftParameter

/**
 * Generates Swift AsyncSequence declarations from SwiftAsyncSequenceSpec.
 *
 * This generates Swift code that bridges Kotlin Flow to Swift AsyncSequence/AsyncStream.
 */
class SwiftAsyncSequenceGenerator {

    /**
     * Generate Swift AsyncSequence declaration.
     */
    fun generate(spec: SwiftAsyncSequenceSpec): String = buildString {
        val elementTypeStr = spec.elementType.swiftRepresentation
        val streamType = "AsyncStream<$elementTypeStr>"

        if (spec.isProperty) {
            generateProperty(spec, streamType)
        } else {
            generateFunction(spec, streamType)
        }
    }

    private fun StringBuilder.generateProperty(spec: SwiftAsyncSequenceSpec, streamType: String) {
        append(spec.accessLevel.swiftKeyword)
        append(" var ")
        append(spec.name)
        append(": ")

        if (spec.isShared && spec.replayCount > 0) {
            // For StateFlow/SharedFlow, we may want a different approach
            append(spec.elementType.swiftRepresentation)
        } else {
            append(streamType)
        }
    }

    private fun StringBuilder.generateFunction(spec: SwiftAsyncSequenceSpec, streamType: String) {
        // Access level
        append(spec.accessLevel.swiftKeyword)
        append(" func ")
        append(spec.name)

        // Type parameters
        if (spec.typeParameters.isNotEmpty()) {
            append("<")
            append(spec.typeParameters.joinToString(", "))
            append(">")
        }

        // Parameters
        append("(")
        append(spec.parameters.joinToString(", ") { it.toSwift() })
        append(")")

        // Return type
        append(" -> ")
        append(streamType)
    }

    private fun SwiftParameter.toSwift(): String = buildString {
        // External name (argument label)
        when {
            externalName == "_" -> append("_ ")
            externalName != null -> {
                append(externalName)
                append(" ")
            }
        }

        // Internal name
        append(name)
        append(": ")

        // Type
        append(type.swiftRepresentation)

        // Default value
        if (defaultValue != null) {
            append(" = ")
            append(defaultValue)
        }
    }

    private val SwiftAsyncSequenceSpec.AccessLevel.swiftKeyword: String
        get() = when (this) {
            SwiftAsyncSequenceSpec.AccessLevel.PUBLIC -> "public"
            SwiftAsyncSequenceSpec.AccessLevel.INTERNAL -> "internal"
            SwiftAsyncSequenceSpec.AccessLevel.PRIVATE -> "private"
        }
}
