package io.swiftify.swift

/**
 * Base exception for all Swiftify errors.
 */
open class SwiftifyException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when analyzing Kotlin source code fails.
 */
class SwiftifyAnalysisException(
    message: String,
    val sourceFile: String? = null,
    val lineNumber: Int? = null,
    cause: Throwable? = null
) : SwiftifyException(buildMessage(message, sourceFile, lineNumber), cause) {

    companion object {
        private fun buildMessage(message: String, sourceFile: String?, lineNumber: Int?): String {
            return buildString {
                append(message)
                if (sourceFile != null) {
                    append(" (file: $sourceFile")
                    if (lineNumber != null) {
                        append(":$lineNumber")
                    }
                    append(")")
                }
            }
        }
    }
}

/**
 * Exception thrown when generating Swift code fails.
 */
class SwiftifyGenerationException(
    message: String,
    val specType: String? = null,
    val specName: String? = null,
    cause: Throwable? = null
) : SwiftifyException(buildMessage(message, specType, specName), cause) {

    companion object {
        private fun buildMessage(message: String, specType: String?, specName: String?): String {
            return buildString {
                append(message)
                if (specType != null || specName != null) {
                    append(" [")
                    if (specType != null) append(specType)
                    if (specName != null) append(": $specName")
                    append("]")
                }
            }
        }
    }
}

/**
 * Exception thrown when configuration is invalid.
 */
class SwiftifyConfigurationException(
    message: String,
    val configKey: String? = null,
    cause: Throwable? = null
) : SwiftifyException(
    if (configKey != null) "$message (key: $configKey)" else message,
    cause
)

/**
 * Exception thrown when a framework processing operation fails.
 */
class SwiftifyFrameworkException(
    message: String,
    val frameworkName: String? = null,
    cause: Throwable? = null
) : SwiftifyException(
    if (frameworkName != null) "$message (framework: $frameworkName)" else message,
    cause
)

/**
 * Exception thrown when Swift compilation fails.
 */
class SwiftifyCompilationException(
    message: String,
    val compilerOutput: String? = null,
    val exitCode: Int? = null,
    cause: Throwable? = null
) : SwiftifyException(message, cause)

/**
 * Exception thrown when a KSP processing operation fails.
 */
class SwiftifyKspException(
    message: String,
    val symbolName: String? = null,
    cause: Throwable? = null
) : SwiftifyException(
    if (symbolName != null) "$message (symbol: $symbolName)" else message,
    cause
)

/**
 * Exception thrown when file I/O operations fail.
 */
class SwiftifyFileException(
    message: String,
    val filePath: String? = null,
    cause: Throwable? = null
) : SwiftifyException(
    if (filePath != null) "$message (path: $filePath)" else message,
    cause
)

/**
 * Container for validation errors that can accumulate multiple issues.
 */
class SwiftifyValidationException(
    val errors: List<ValidationError>
) : SwiftifyException(buildMessage(errors)) {

    constructor(error: ValidationError) : this(listOf(error))
    constructor(message: String) : this(listOf(ValidationError(message)))

    companion object {
        private fun buildMessage(errors: List<ValidationError>): String {
            return if (errors.size == 1) {
                errors.first().message
            } else {
                "Multiple validation errors:\n" + errors.joinToString("\n") { "  - ${it.message}" }
            }
        }
    }

    data class ValidationError(
        val message: String,
        val field: String? = null,
        val value: Any? = null
    )
}
