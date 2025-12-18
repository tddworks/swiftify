package io.swiftify.generator

import io.swiftify.common.SwiftEnumCase
import io.swiftify.common.SwiftEnumSpec

/**
 * Generates Swift enum source code from a SwiftEnumSpec.
 *
 * This generator produces clean, idiomatic Swift code that can be
 * compiled alongside the Kotlin/Native framework.
 */
class SwiftEnumGenerator {

    /**
     * Generate Swift source code for the given enum specification.
     */
    fun generate(spec: SwiftEnumSpec): String = buildString {
        // Add @frozen attribute if exhaustive
        if (spec.isExhaustive) {
            appendLine("@frozen")
        }

        // Build the enum declaration line
        append(spec.accessLevel.swiftKeyword)
        append(" enum ")
        append(spec.name)

        // Add type parameters if present
        if (spec.typeParameters.isNotEmpty()) {
            append("<")
            append(spec.typeParameters.joinToString(", "))
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
                append(case.associatedValues.joinToString(", ") { av ->
                    "${av.label}: ${av.type.swiftRepresentation}"
                })
                append(")")
            }
            appendLine()
        }

        append("}")
    }

    private val SwiftEnumSpec.AccessLevel.swiftKeyword: String
        get() = when (this) {
            SwiftEnumSpec.AccessLevel.PUBLIC -> "public"
            SwiftEnumSpec.AccessLevel.INTERNAL -> "internal"
            SwiftEnumSpec.AccessLevel.PRIVATE -> "private"
        }
}
