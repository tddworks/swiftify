package io.swiftify.generator

import io.swiftify.swift.SwiftDefaultsSpec
import io.swiftify.swift.SwiftParameter
import io.swiftify.swift.SwiftType
import io.swiftify.swift.SwiftifyGenerationException
import io.swiftify.swift.SwiftifyValidationException

/**
 * Generates Swift async function implementations from SwiftDefaultsSpec.
 *
 * Since Kotlin 1.8+, the Kotlin/Native compiler generates async/await methods natively.
 * This generator now focuses on:
 * 1. Generating convenience overloads for functions with default parameters
 *    (Kotlin doesn't preserve default values in Swift)
 * 2. Preview mode signatures for documentation
 *
 * For functions WITHOUT default parameters, no generation is needed as Kotlin provides them.
 */
class SwiftDefaultsGenerator {
    /**
     * Generate Swift async function declaration (signature only, for preview).
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftDefaultsSpec): String {
        validate(spec)
        return try {
            generateSignature(spec)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift async function",
                specType = "asyncFunction",
                specName = spec.name,
                cause = e,
            )
        }
    }

    /**
     * Generate Swift async function with full implementation.
     *
     * NOTE: Since Kotlin 1.8+ generates async/await natively, this should only be used
     * for special cases. For functions with default parameters, use
     * generateConvenienceOverloads() instead.
     *
     * @param className The Kotlin class name that contains this method
     * @return Complete Swift function with bridging implementation
     */
    fun generateWithImplementation(
        spec: SwiftDefaultsSpec,
        className: String? = null,
    ): String {
        validate(spec)
        return try {
            generateImplementation(spec, className)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift async function implementation",
                specType = "asyncFunction",
                specName = spec.name,
                cause = e,
            )
        }
    }

    /**
     * Generate convenience overloads for functions with default parameters.
     *
     * Since Kotlin doesn't preserve default parameter values in Swift, and we can't
     * duplicate the full signature (Kotlin already generates it), we generate
     * DISTINCT overloads with fewer parameters that call the full Kotlin method.
     *
     * For example, for `func foo(a: Int, b: Bool = true, c: String = "x")`:
     * - Kotlin generates: `func foo(a: Int, b: Bool, c: String) async throws`
     * - We generate:
     *   - `func foo(a: Int) async throws` -> calls foo(a, true, "x")
     *   - `func foo(a: Int, b: Bool) async throws` -> calls foo(a, b, "x")
     *
     * Note: We don't generate a no-arg version if all params have defaults,
     * nor do we generate the full signature (Kotlin already has it).
     *
     * @param className The Kotlin class name that contains this method
     * @param maxOverloads Maximum number of overloads to generate
     * @return Convenience overloads that call the full Kotlin method, or empty string if none needed
     */
    fun generateConvenienceOverloads(
        spec: SwiftDefaultsSpec,
        className: String? = null,
        maxOverloads: Int = 5,
    ): String {
        validate(spec)
        return try {
            generateConvenienceOverloadsInternal(spec, className, maxOverloads)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift async function convenience overloads",
                specType = "asyncFunction",
                specName = spec.name,
                cause = e,
            )
        }
    }

    /**
     * Generate convenience overload function bodies only (without extension wrapper).
     * Used for grouped extension generation where multiple functions share one extension.
     *
     * @return List of function bodies for convenience overloads, empty if none needed
     */
    fun generateConvenienceOverloadBodies(spec: SwiftDefaultsSpec): List<String> {
        validate(spec)
        val paramsWithDefaults = spec.parameters.filter { it.defaultValue != null }
        if (paramsWithDefaults.isEmpty()) {
            return emptyList()
        }
        return generateOverloadBodies(spec)
    }

    /**
     * Generate convenience overloads that call the full Kotlin method.
     *
     * These are DISTINCT from the full signature - they have fewer parameters
     * and call the full method with default values filled in.
     */
    private fun generateConvenienceOverloadsInternal(
        spec: SwiftDefaultsSpec,
        className: String?,
        maxOverloads: Int,
    ): String {
        val paramsWithDefaults = spec.parameters.filter { it.defaultValue != null }
        if (paramsWithDefaults.isEmpty()) {
            // No default parameters, nothing to generate
            // (Kotlin already provides the full method)
            return ""
        }

        val overloadBodies = generateOverloadBodies(spec, maxOverloads)
        if (overloadBodies.isEmpty()) {
            return ""
        }

        return if (className != null) {
            buildString {
                appendLine("extension $className {")
                append(overloadBodies.joinToString("\n\n") { "    $it".replace("\n", "\n    ").trimEnd() })
                appendLine()
                append("}")
            }
        } else {
            overloadBodies.joinToString("\n\n")
        }
    }

    /**
     * Generate convenience overload bodies (without extension wrapper).
     *
     * Generates overloads with progressively fewer parameters,
     * EXCLUDING the full signature (which Kotlin already provides).
     */
    private fun generateOverloadBodies(
        spec: SwiftDefaultsSpec,
        maxOverloads: Int = 5,
    ): List<String> {
        val paramsWithDefaults = spec.parameters.filter { it.defaultValue != null }
        if (paramsWithDefaults.isEmpty()) {
            return emptyList()
        }

        val overloads = mutableListOf<String>()

        // Split into required and optional parameters
        val requiredParams = spec.parameters.takeWhile { it.defaultValue == null }
        val defaultParams = spec.parameters.dropWhile { it.defaultValue == null }

        // Generate overloads from just-required up to (but not including) all params
        // We skip the full signature because Kotlin already generates it
        for (i in 0 until defaultParams.size) {
            if (overloads.size >= maxOverloads) break

            val paramsForOverload = requiredParams + defaultParams.take(i)
            val overloadSpec =
                spec.copy(
                    parameters = paramsForOverload.map { it.copy(defaultValue = null) },
                )

            val body = generateConvenienceOverloadBody(overloadSpec, spec)
            overloads.add(body)
        }

        return overloads
    }

    /**
     * Generate a single convenience overload body that calls the full Kotlin method.
     */
    private fun generateConvenienceOverloadBody(
        overloadSpec: SwiftDefaultsSpec,
        fullSpec: SwiftDefaultsSpec,
    ): String = buildString {
        // Generate signature without default values
        append(overloadSpec.accessLevel.swiftKeyword)
        append(" func ")
        append(overloadSpec.name)
        if (overloadSpec.typeParameters.isNotEmpty()) {
            append("<")
            append(overloadSpec.typeParameters.joinToString(", "))
            append(">")
        }
        append("(")
        append(
            overloadSpec.parameters.joinToString(", ") { param ->
                buildString {
                    if (param.externalName == "_") {
                        append("_ ")
                    } else if (param.externalName != null) {
                        append("${param.externalName} ")
                    }
                    append("${param.name}: ${param.type.swiftRepresentation}")
                    // No default value - this is a distinct overload
                }
            },
        )
        append(")")
        if (overloadSpec.isAsync) append(" async")
        if (overloadSpec.isThrowing) append(" throws")
        if (fullSpec.returnType !is SwiftType.Void) {
            append(" -> ")
            append(fullSpec.returnType.swiftRepresentation)
        }
        appendLine(" {")

        // Call the full Kotlin method with default values filled in
        val indent = "    "
        if (fullSpec.returnType !is SwiftType.Void) {
            append("${indent}return ")
        } else {
            append(indent)
        }
        if (overloadSpec.isThrowing) append("try ")
        if (overloadSpec.isAsync) append("await ")
        append(fullSpec.name)
        append("(")

        val args =
            fullSpec.parameters.mapIndexed { index, param ->
                val overloadParam = overloadSpec.parameters.getOrNull(index)
                if (overloadParam != null) {
                    // Parameter exists in overload, pass it through
                    "${param.name}: ${param.name}"
                } else {
                    // Parameter not in overload, use default value
                    val defaultValue = fullSpec.parameters[index].defaultValue ?: "nil"
                    "${param.name}: $defaultValue"
                }
            }
        append(args.joinToString(", "))
        appendLine(")")
        append("}")
    }

    /**
     * Generate function body only (without extension wrapper).
     * Used for grouped extension generation where multiple functions share one extension.
     */
    fun generateFunctionBody(spec: SwiftDefaultsSpec): String {
        validate(spec)
        return generateFunctionBodyInternal(spec)
    }

    /**
     * Internal function body generation.
     */
    private fun generateFunctionBodyInternal(spec: SwiftDefaultsSpec): String = buildString {
        // Signature
        append(generateSignature(spec))
        appendLine(" {")

        val indent = "    "
        val returnType = spec.returnType

        if (returnType is SwiftType.Void) {
            // Need explicit generic type for Void since Swift can't infer it
            if (spec.isThrowing) {
                appendLine("${indent}try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in")
            } else {
                appendLine("${indent}await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in")
            }

            append("$indent    self.${spec.name}(")
            spec.parameters.forEachIndexed { index, param ->
                if (index > 0) append(", ")
                append("${param.name}: ${param.name}")
            }
            if (spec.parameters.isNotEmpty()) append(", ")
            appendLine("completionHandler: { error in")

            if (spec.isThrowing) {
                appendLine("$indent        if let error = error {")
                appendLine("$indent            continuation.resume(throwing: error)")
                appendLine("$indent        } else {")
                appendLine("$indent            continuation.resume(returning: ())")
                appendLine("$indent        }")
            } else {
                appendLine("$indent        continuation.resume(returning: ())")
            }
            appendLine("$indent    })")
            appendLine("$indent}")
        } else {
            if (spec.isThrowing) {
                appendLine("${indent}return try await withCheckedThrowingContinuation { continuation in")
            } else {
                appendLine("${indent}return await withCheckedContinuation { continuation in")
            }

            append("$indent    self.${spec.name}(")
            spec.parameters.forEachIndexed { index, param ->
                if (index > 0) append(", ")
                append("${param.name}: ${param.name}")
            }
            if (spec.parameters.isNotEmpty()) append(", ")
            appendLine("completionHandler: { result, error in")

            if (spec.isThrowing) {
                appendLine("$indent        if let error = error {")
                appendLine("$indent            continuation.resume(throwing: error)")
                appendLine("$indent        } else if let result = result {")
                appendLine("$indent            continuation.resume(returning: result)")
                appendLine("$indent        }")
            } else {
                appendLine("$indent        continuation.resume(returning: result!)")
            }
            appendLine("$indent    })")
            appendLine("$indent}")
        }

        append("}")
    }

    /**
     * Generate the full implementation that bridges to Kotlin.
     */
    private fun generateImplementation(
        spec: SwiftDefaultsSpec,
        className: String?,
    ): String = buildString {
        val baseIndent = if (className != null) "    " else ""
        val indent = "$baseIndent    "

        // Wrap in extension if class name provided
        if (className != null) {
            appendLine("extension $className {")
        }

        // Signature
        append(baseIndent)
        append(generateSignature(spec))
        appendLine(" {")

        val returnType = spec.returnType

        if (returnType is SwiftType.Void) {
            // Void return - use withCheckedThrowingContinuation without return value
            if (spec.isThrowing) {
                appendLine("${indent}try await withCheckedThrowingContinuation { continuation in")
            } else {
                appendLine("${indent}await withCheckedContinuation { continuation in")
            }

            // Call the Kotlin function (use self. for class methods)
            append("$indent    ")
            if (className != null) {
                append("self.")
            }
            append("${spec.name}(")
            spec.parameters.forEachIndexed { index, param ->
                if (index > 0) append(", ")
                append("${param.name}: ${param.name}")
            }
            if (spec.parameters.isNotEmpty()) append(", ")
            appendLine("completionHandler: { error in")

            if (spec.isThrowing) {
                appendLine("$indent        if let error = error {")
                appendLine("$indent            continuation.resume(throwing: error)")
                appendLine("$indent        } else {")
                appendLine("$indent            continuation.resume(returning: ())")
                appendLine("$indent        }")
            } else {
                appendLine("$indent        continuation.resume(returning: ())")
            }
            appendLine("$indent    })")
            appendLine("$indent}")
        } else {
            // Has return value
            if (spec.isThrowing) {
                appendLine("${indent}return try await withCheckedThrowingContinuation { continuation in")
            } else {
                appendLine("${indent}return await withCheckedContinuation { continuation in")
            }

            // Call the Kotlin function (use self. for class methods)
            append("$indent    ")
            if (className != null) {
                append("self.")
            }
            append("${spec.name}(")
            spec.parameters.forEachIndexed { index, param ->
                if (index > 0) append(", ")
                append("${param.name}: ${param.name}")
            }
            if (spec.parameters.isNotEmpty()) append(", ")
            appendLine("completionHandler: { result, error in")

            if (spec.isThrowing) {
                appendLine("$indent        if let error = error {")
                appendLine("$indent            continuation.resume(throwing: error)")
                appendLine("$indent        } else if let result = result {")
                appendLine("$indent            continuation.resume(returning: result)")
                appendLine("$indent        }")
            } else {
                appendLine("$indent        continuation.resume(returning: result!)")
            }
            appendLine("$indent    })")
            appendLine("$indent}")
        }

        append(baseIndent)
        append("}")

        // Close extension
        if (className != null) {
            appendLine()
            append("}")
        }
    }

    private fun validate(spec: SwiftDefaultsSpec) {
        val errors = mutableListOf<SwiftifyValidationException.ValidationError>()

        if (spec.name.isBlank()) {
            errors.add(
                SwiftifyValidationException.ValidationError(
                    "Function name cannot be blank",
                    field = "name",
                ),
            )
        }

        spec.parameters.forEachIndexed { index, param ->
            if (param.name.isBlank()) {
                errors.add(
                    SwiftifyValidationException.ValidationError(
                        "Parameter name cannot be blank",
                        field = "parameters[$index].name",
                    ),
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw SwiftifyValidationException(errors)
        }
    }

    private fun generateSignature(spec: SwiftDefaultsSpec): String = buildString {
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

        // Async modifier (only for async/suspend functions)
        if (spec.isAsync) {
            append(" async")
        }

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

    private val SwiftDefaultsSpec.AccessLevel.swiftKeyword: String
        get() =
            when (this) {
                SwiftDefaultsSpec.AccessLevel.PUBLIC -> "public"
                SwiftDefaultsSpec.AccessLevel.INTERNAL -> "internal"
                SwiftDefaultsSpec.AccessLevel.PRIVATE -> "private"
            }
}
