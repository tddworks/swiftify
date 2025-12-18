package io.swiftify.generator

import io.swiftify.common.SwiftAsyncFunctionSpec
import io.swiftify.common.SwiftParameter
import io.swiftify.common.SwiftType
import io.swiftify.common.SwiftifyGenerationException
import io.swiftify.common.SwiftifyValidationException

/**
 * Generates Swift async function implementations from SwiftAsyncFunctionSpec.
 *
 * This generates Swift code that bridges Kotlin suspend functions to Swift async/await
 * using withCheckedThrowingContinuation to wrap the Kotlin completion handler.
 */
class SwiftAsyncFunctionGenerator {

    /**
     * Generate Swift async function declaration (signature only, for preview).
     *
     * @throws SwiftifyValidationException if the spec is invalid
     * @throws SwiftifyGenerationException if code generation fails
     */
    fun generate(spec: SwiftAsyncFunctionSpec): String {
        validate(spec)
        return try {
            generateSignature(spec)
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

    /**
     * Generate Swift async function with full implementation.
     *
     * @param className The Kotlin class name that contains this method
     * @return Complete Swift function with bridging implementation
     */
    fun generateWithImplementation(spec: SwiftAsyncFunctionSpec, className: String? = null): String {
        validate(spec)
        return try {
            generateImplementation(spec, className)
        } catch (e: Exception) {
            if (e is SwiftifyValidationException) throw e
            throw SwiftifyGenerationException(
                "Failed to generate Swift async function implementation",
                specType = "asyncFunction",
                specName = spec.name,
                cause = e
            )
        }
    }

    /**
     * Generate Swift async function with default parameters preserved.
     *
     * For a function like `func foo(a: Int, b: Bool = true, c: String = "x")`,
     * generates a single Swift function with default parameters:
     * `func foo(a: Int, b: Bool = true, c: String = "x") async throws`
     *
     * This uses Swift's native default parameter syntax instead of generating multiple overloads.
     *
     * @param maxOverloads Ignored - kept for API compatibility
     * @return Single function declaration with default parameters
     */
    fun generateWithOverloads(spec: SwiftAsyncFunctionSpec, maxOverloads: Int = 5): String {
        validate(spec)
        return try {
            // Just generate the single signature with default parameters preserved
            generateSignature(spec)
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

    /**
     * Generate Swift async function with default parameters and full implementation.
     *
     * Generates a single Swift function that preserves default parameters and includes
     * the bridging implementation to call the underlying Kotlin function.
     *
     * @param className The Kotlin class name that contains this method
     * @param maxOverloads Ignored - kept for API compatibility
     * @return Single function with default parameters and bridging implementation
     */
    fun generateWithOverloadsAndImplementation(
        spec: SwiftAsyncFunctionSpec,
        className: String? = null,
        maxOverloads: Int = 5
    ): String {
        validate(spec)
        return try {
            // Generate single function with default parameters and implementation
            generateImplementation(spec, className)
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

    /**
     * Generate list of overload declarations (signatures only).
     */
    private fun generateOverloads(spec: SwiftAsyncFunctionSpec, maxOverloads: Int): List<String> {
        return generateOverloadSpecs(spec, maxOverloads).map { generateSignature(it) }
    }

    /**
     * Generate list of overload specs.
     */
    private fun generateOverloadSpecs(spec: SwiftAsyncFunctionSpec, maxOverloads: Int): List<SwiftAsyncFunctionSpec> {
        val paramsWithDefaults = spec.parameters.filter { it.defaultValue != null }

        // If no default parameters, just return the single function
        if (paramsWithDefaults.isEmpty()) {
            return listOf(spec.copy(parameters = spec.parameters.map { it.copy(defaultValue = null) }))
        }

        val overloads = mutableListOf<SwiftAsyncFunctionSpec>()

        // Generate overloads by progressively removing default parameters from the end
        val requiredParams = spec.parameters.takeWhile { it.defaultValue == null }
        val defaultParams = spec.parameters.dropWhile { it.defaultValue == null }

        // Generate overloads: start with just required, add one default param at a time
        var overloadCount = 0
        for (i in 0..defaultParams.size) {
            if (overloadCount >= maxOverloads) break

            val paramsForOverload = requiredParams + defaultParams.take(i)
            val overloadSpec = spec.copy(
                parameters = paramsForOverload.map { it.copy(defaultValue = null) }
            )
            overloads.add(overloadSpec)
            overloadCount++
        }

        return overloads
    }

    /**
     * Generate implementation for an overload that calls the full function.
     */
    private fun generateOverloadImplementation(
        overloadSpec: SwiftAsyncFunctionSpec,
        fullSpec: SwiftAsyncFunctionSpec,
        className: String?
    ): String = buildString {
        // Signature
        append(generateSignature(overloadSpec))
        appendLine(" {")

        // Call the full function with default values
        val indent = "    "
        if (fullSpec.returnType !is SwiftType.Void) {
            append("${indent}return ")
        } else {
            append(indent)
        }

        if (overloadSpec.isThrowing) {
            append("try ")
        }
        append("await ")
        append(fullSpec.name)
        append("(")

        val args = fullSpec.parameters.mapIndexed { index, param ->
            val overloadParam = overloadSpec.parameters.getOrNull(index)
            if (overloadParam != null) {
                "${param.name}: ${param.name}"
            } else {
                // Use default value
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
    fun generateFunctionBody(spec: SwiftAsyncFunctionSpec): String {
        validate(spec)
        return generateFunctionBodyInternal(spec)
    }

    /**
     * Internal function body generation.
     */
    private fun generateFunctionBodyInternal(spec: SwiftAsyncFunctionSpec): String = buildString {
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
    private fun generateImplementation(spec: SwiftAsyncFunctionSpec, className: String?): String = buildString {
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

    private fun generateSignature(spec: SwiftAsyncFunctionSpec): String = buildString {
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
