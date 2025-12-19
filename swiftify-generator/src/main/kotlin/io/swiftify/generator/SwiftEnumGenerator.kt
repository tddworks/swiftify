package io.swiftify.generator

import io.swiftify.swift.SwiftEnumCase
import io.swiftify.swift.SwiftEnumSpec
import io.swiftify.swift.SwiftifyGenerationException
import io.swiftify.swift.SwiftifyValidationException

/**
 * Generates Swift enum source code from a SwiftEnumSpec.
 *
 * This generator produces clean, idiomatic Swift code that can be
 * compiled alongside the Kotlin/Native framework.
 */
class SwiftEnumGenerator {
    /**
     * Generate Swift source code for the given enum specification.
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftEnumSpec): String {
        validate(spec)
        return try {
            generateCode(spec)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift enum",
                specType = "enum",
                specName = spec.name,
                cause = e,
            )
        }
    }

    private fun validate(spec: SwiftEnumSpec) {
        val errors = mutableListOf<SwiftifyValidationException.ValidationError>()

        if (spec.name.isBlank()) {
            errors.add(
                SwiftifyValidationException.ValidationError(
                    "Enum name cannot be blank",
                    field = "name",
                ),
            )
        }

        if (spec.name.isNotBlank() && !spec.name.first().isUpperCase()) {
            errors.add(
                SwiftifyValidationException.ValidationError(
                    "Enum name should start with uppercase letter",
                    field = "name",
                    value = spec.name,
                ),
            )
        }

        if (spec.cases.isEmpty()) {
            errors.add(
                SwiftifyValidationException.ValidationError(
                    "Enum must have at least one case",
                    field = "cases",
                ),
            )
        }

        spec.cases.forEachIndexed { index, case ->
            if (case.name.isBlank()) {
                errors.add(
                    SwiftifyValidationException.ValidationError(
                        "Case name cannot be blank",
                        field = "cases[$index].name",
                    ),
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw SwiftifyValidationException(errors)
        }
    }

    private fun generateCode(spec: SwiftEnumSpec): String = buildString {
        // Add @frozen attribute if exhaustive
        if (spec.isExhaustive) {
            appendLine("@frozen")
        }

        // Build the enum declaration line
        append(spec.accessLevel.swiftKeyword)
        append(" enum ")
        append(spec.name)

        // Add type parameters if present (strip Kotlin variance modifiers)
        if (spec.typeParameters.isNotEmpty()) {
            append("<")
            append(
                spec.typeParameters.joinToString(", ") { param ->
                    // Remove 'out' and 'in' variance modifiers for Swift
                    param.removePrefix("out ").removePrefix("in ").trim()
                },
            )
            append(">")
        }

        // Add protocol conformances if present
        if (spec.conformances.isNotEmpty()) {
            append(": ")
            append(spec.conformances.joinToString(", "))
        }

        appendLine(" {")

        // Generate each case
        spec.cases.forEach { case ->
            append("    case ")
            append(case.name)

            if (case.associatedValues.isNotEmpty()) {
                append("(")
                append(
                    case.associatedValues.joinToString(", ") { av ->
                        "${av.label}: ${av.type.swiftRepresentation}"
                    },
                )
                append(")")
            }
            appendLine()
        }

        append("}")
    }

    private val SwiftEnumSpec.AccessLevel.swiftKeyword: String
        get() =
            when (this) {
                SwiftEnumSpec.AccessLevel.PUBLIC -> "public"
                SwiftEnumSpec.AccessLevel.INTERNAL -> "internal"
                SwiftEnumSpec.AccessLevel.PRIVATE -> "private"
            }
}
