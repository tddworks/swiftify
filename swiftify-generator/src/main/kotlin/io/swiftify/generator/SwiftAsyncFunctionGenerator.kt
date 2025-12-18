package io.swiftify.generator

import io.swiftify.common.SwiftAsyncFunctionSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import io.swiftify.common.SwiftifyGenerationException
import io.swiftify.common.SwiftifyValidationException

/**
 * Generates Swift async function declarations from SwiftAsyncFunctionSpec.
 *
 * This generates the Swift function signature that bridges Kotlin suspend functions
 * to Swift async/await.
 */
class SwiftAsyncFunctionGenerator {

    /**
     * Generate Swift async function declaration.
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftAsyncFunctionSpec): String {
        validate(spec)
        return try {
            generateCode(spec)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift async function",
                specType = "asyncFunction",
                specName = spec.name,
                cause = e
            )
        }
    }

    private fun validate(spec: SwiftAsyncFunctionSpec) {
        val errors = mutableListOf<SwiftifyValidationException.ValidationError>()

        if (spec.name.isBlank()) {
            errors.add(SwiftifyValidationException.ValidationError(
                "Function name cannot be blank",
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

    private fun generateCode(spec: SwiftAsyncFunctionSpec): String = buildString {
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

        // Async modifier (always present for async functions)
        append(" async")

        // Throws modifier
        if (spec.isThrowing) {
            append(" throws")
        }

        // Return type (only if not Void)
        if (spec.returnType !is SwiftType.Void) {
            append(" -> ")
            append(spec.returnType.swiftRepresentation)
        }
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

        // Inout modifier
        if (isInout) {
            append("inout ")
        }

        // Type
        append(type.swiftRepresentation)

        // Variadic
        if (isVariadic) {
            append("...")
        }

        // Default value
        if (defaultValue != null) {
            append(" = ")
            append(defaultValue)
        }
    }

    private val SwiftAsyncFunctionSpec.AccessLevel.swiftKeyword: String
        get() = when (this) {
            SwiftAsyncFunctionSpec.AccessLevel.PUBLIC -> "public"
            SwiftAsyncFunctionSpec.AccessLevel.INTERNAL -> "internal"
            SwiftAsyncFunctionSpec.AccessLevel.PRIVATE -> "private"
        }
}
