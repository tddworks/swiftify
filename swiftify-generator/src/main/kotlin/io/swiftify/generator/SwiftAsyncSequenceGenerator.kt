package io.swiftify.generator

import io.swiftify.common.SwiftAsyncSequenceSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftifyGenerationException
import io.swiftify.common.SwiftifyValidationException

/**
 * Generates Swift AsyncSequence implementations from SwiftAsyncSequenceSpec.
 *
 * This generates Swift code that bridges Kotlin Flow to Swift AsyncSequence/AsyncStream
 * using Swift's AsyncStream API.
 */
class SwiftAsyncSequenceGenerator {

    /**
     * Generate Swift AsyncSequence declaration (signature only).
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftAsyncSequenceSpec): String {
        validate(spec)
        return try {
            generateSignature(spec)
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

    /**
     * Generate Swift AsyncSequence with full implementation.
     *
     * @param className The Kotlin class name that contains this method/property
     * @return Complete Swift function/property with bridging implementation
     */
    fun generateWithImplementation(spec: SwiftAsyncSequenceSpec, className: String? = null): String {
        validate(spec)
        return try {
            generateImplementation(spec, className)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift AsyncSequence implementation",
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

    private fun generateSignature(spec: SwiftAsyncSequenceSpec): String = buildString {
        val elementTypeStr = spec.elementType.swiftRepresentation
        val streamType = "AsyncStream<$elementTypeStr>"

        if (spec.isProperty) {
            generatePropertySignature(spec, streamType)
        } else {
            generateFunctionSignature(spec, streamType)
        }
    }

    private fun generateImplementation(spec: SwiftAsyncSequenceSpec, className: String?): String = buildString {
        val elementTypeStr = spec.elementType.swiftRepresentation
        val streamType = "AsyncStream<$elementTypeStr>"

        if (spec.isProperty) {
            generatePropertyImplementation(spec, streamType, className)
        } else {
            generateFunctionImplementation(spec, streamType, className)
        }
    }

    private fun StringBuilder.generatePropertySignature(spec: SwiftAsyncSequenceSpec, streamType: String) {
        append(spec.accessLevel.swiftKeyword)
        append(" var ")
        append(spec.name)
        append(": ")
        append(streamType)
    }

    private fun StringBuilder.generatePropertyImplementation(
        spec: SwiftAsyncSequenceSpec,
        streamType: String,
        className: String?
    ) {
        val elementTypeStr = spec.elementType.swiftRepresentation
        val indent = "    "

        append(spec.accessLevel.swiftKeyword)
        append(" var ")
        append(spec.name)
        append(": ")
        appendLine("$streamType {")

        appendLine("${indent}return AsyncStream { continuation in")
        appendLine("$indent    let collector = SwiftifyFlowCollector<$elementTypeStr>(")
        appendLine("$indent        onEmit: { value in")
        appendLine("$indent            continuation.yield(value)")
        appendLine("$indent        },")
        appendLine("$indent        onComplete: {")
        appendLine("$indent            continuation.finish()")
        appendLine("$indent        },")
        appendLine("$indent        onError: { _ in")
        appendLine("$indent            continuation.finish()")
        appendLine("$indent        }")
        appendLine("$indent    )")

        // Call the Kotlin property
        append("$indent    ")
        if (className != null) {
            append("self.")
        }
        appendLine("__${spec.name}.collect(collector: collector, completionHandler: { _ in })")
        appendLine("$indent}")
        append("}")
    }

    private fun StringBuilder.generateFunctionSignature(spec: SwiftAsyncSequenceSpec, streamType: String) {
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

    private fun StringBuilder.generateFunctionImplementation(
        spec: SwiftAsyncSequenceSpec,
        streamType: String,
        className: String?
    ) {
        val elementTypeStr = spec.elementType.swiftRepresentation
        val indent = "    "

        // Signature
        generateFunctionSignature(spec, streamType)
        appendLine(" {")

        appendLine("${indent}return AsyncStream { continuation in")
        appendLine("$indent    let collector = SwiftifyFlowCollector<$elementTypeStr>(")
        appendLine("$indent        onEmit: { value in")
        appendLine("$indent            continuation.yield(value)")
        appendLine("$indent        },")
        appendLine("$indent        onComplete: {")
        appendLine("$indent            continuation.finish()")
        appendLine("$indent        },")
        appendLine("$indent        onError: { _ in")
        appendLine("$indent            continuation.finish()")
        appendLine("$indent        }")
        appendLine("$indent    )")

        // Call the Kotlin function
        append("$indent    ")
        if (className != null) {
            append("self.")
        }
        append("__${spec.name}(")
        spec.parameters.forEachIndexed { index, param ->
            if (index > 0) append(", ")
            append("${param.name}: ${param.name}")
        }
        appendLine(").collect(collector: collector, completionHandler: { _ in })")
        appendLine("$indent}")
        append("}")
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
