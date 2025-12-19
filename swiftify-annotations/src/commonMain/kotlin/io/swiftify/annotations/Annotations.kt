package io.swiftify.annotations

/**
 * Marks a sealed class to be transformed into a Swift enum.
 *
 * Example:
 * ```kotlin
 * @SwiftEnum(name = "NetworkResult", exhaustive = true)
 * sealed class NetworkResult {
 *     data class Success(val data: String) : NetworkResult()
 *     data class Failure(val error: Throwable) : NetworkResult()
 * }
 * ```
 *
 * Will generate:
 * ```swift
 * @frozen
 * public enum NetworkResult: Hashable {
 *     case success(data: String)
 *     case failure(error: Error)
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftEnum(
    /**
     * The name of the Swift enum. If empty, uses the Kotlin class name.
     */
    val name: String = "",
    /**
     * Whether this enum is exhaustive (adds @frozen attribute).
     * Exhaustive enums enable exhaustive switch statements in Swift.
     */
    val exhaustive: Boolean = true,
    /**
     * Protocol conformances for the enum (e.g., "Hashable", "Codable").
     */
    val conformances: Array<String> = [],
)

/**
 * Customizes how a sealed subclass is represented as an enum case.
 *
 * Example:
 * ```kotlin
 * @SwiftEnum
 * sealed class State {
 *     @SwiftCase("idle")
 *     object Idle : State()
 *
 *     @SwiftCase("loading", associated = "progress: Double")
 *     data class Loading(val progress: Double) : State()
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftCase(
    /**
     * The Swift case name. If empty, uses lowercased Kotlin class name.
     */
    val name: String = "",
    /**
     * Associated value declaration (e.g., "value: String, code: Int").
     */
    val associated: String = "",
)

/**
 * @deprecated Use @SwiftDefaults instead. Kotlin 2.0+ already exports suspend functions
 * as Swift async/await automatically. Use @SwiftDefaults to generate convenience overloads
 * for functions with default parameters.
 */
@Deprecated(
    message = "Use @SwiftDefaults instead. Kotlin 2.0+ exports suspend as async automatically.",
    replaceWith = ReplaceWith("SwiftDefaults"),
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftAsync(
    val throwing: Boolean = true,
    val name: String = "",
)

/**
 * Marks a Flow-returning function to be exposed as Swift AsyncSequence.
 *
 * Example:
 * ```kotlin
 * @SwiftFlow
 * fun observeUpdates(): Flow<Update>
 * ```
 *
 * Will generate a Swift function returning AsyncSequence.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftFlow(
    /**
     * Custom Swift function/property name.
     */
    val name: String = "",
)

/**
 * Generates Swift convenience overloads for functions with default parameters.
 *
 * Swift doesn't support default parameters from Kotlin/Objective-C interfaces.
 * This annotation generates overloaded methods that call through with default values.
 *
 * Example:
 * ```kotlin
 * @SwiftDefaults
 * suspend fun getNotes(limit: Int = 10, includeArchived: Boolean = false): List<Note>
 * ```
 *
 * Generates Swift overloads:
 * ```swift
 * extension NotesRepository {
 *     func getNotes() async throws -> [Note] {
 *         return try await getNotes(limit: 10, includeArchived: false)
 *     }
 *     func getNotes(limit: Int32) async throws -> [Note] {
 *         return try await getNotes(limit: limit, includeArchived: false)
 *     }
 *     // Full signature provided by Kotlin/Native
 * }
 * ```
 *
 * Works with both suspend and regular functions.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftDefaults(
    /**
     * Whether to generate convenience overloads with default values.
     * Set to false to disable generation for a specific function.
     */
    val generate: Boolean = true,
    /**
     * Maximum number of default argument combinations to generate.
     * Limits combinatorial explosion for functions with many defaults.
     */
    val maxOverloads: Int = 5,
)

/**
 * Excludes a declaration from Swiftify transformations.
 *
 * Use this to prevent specific classes or functions from being processed.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftifyIgnore

/**
 * Customizes the Swift name of a declaration.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftName(
    /**
     * The name to use in Swift.
     */
    val name: String,
)

/**
 * Customizes a parameter's Swift representation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftParameter(
    /**
     * External parameter name (argument label). Use "_" for no label.
     */
    val label: String = "",
    /**
     * Default value expression in Swift syntax.
     */
    val defaultValue: String = "",
)
