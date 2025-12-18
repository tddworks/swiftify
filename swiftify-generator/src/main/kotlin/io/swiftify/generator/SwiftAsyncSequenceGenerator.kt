package io.swiftify.generator

import io.swiftify.common.SwiftAsyncSequenceSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftifyGenerationException
import io.swiftify.common.SwiftifyValidationException

/**
 * Generates Swift AsyncSequence declarations from SwiftAsyncSequenceSpec.
 *
 * This generates Swift code that bridges Kotlin Flow to Swift AsyncSequence/AsyncStream.
 */
class SwiftAsyncSequenceGenerator {

    /**
     * Generate Swift AsyncSequence declaration.
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftAsyncSequenceSpec): String {
        validate(spec)
        return try {
            generateCode(spec)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift AsyncSequence",
                specType = "asyncSequence",
                specName = spec.name,
                cause = e
            )
        }
    }

    private fun validate(spec: SwiftAsyncSequenceSpec) {
        val errors = mutableListOf<SwiftifyValidationException.ValidationError>()

        if (spec.name.isBlank()) {
            errors.add(SwiftifyValidationException.ValidationError(
                "AsyncSequence name cannot be blank",
                field = "name"
            ))
        }

        spec.parameters.forEachIndexed { index, param ->
            if (param.name.isBlank()) {
                errors.add(SwiftifyValidationException.ValidationError(
                    "Parameter name cannot be blank",
                    field = "parameters[$index].name"
                ))
            }
        }

        if (errors.isNotEmpty()) {
            throw SwiftifyValidationException(errors)
        }
    }

    private fun generateCode(spec: SwiftAsyncSequenceSpec): String = buildString {
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
