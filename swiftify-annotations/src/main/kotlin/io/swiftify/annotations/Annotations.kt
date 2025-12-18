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
    val conformances: Array<String> = []
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
    val associated: String = ""
)

/**
 * Marks a suspend function to be exposed as a Swift async function.
 *
 * Example:
 * ```kotlin
 * @SwiftAsync
 * suspend fun fetchData(id: String): Result
 * ```
 *
 * Will generate:
 * ```swift
 * public func fetchData(id: String) async throws -> Result
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftAsync(
    /**
     * Whether this function can throw errors (adds `throws` keyword).
     */
    val throwing: Boolean = true,

    /**
     * Custom Swift function name. If empty, uses the Kotlin function name.
     */
    val name: String = ""
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
    val name: String = ""
)

/**
 * Configures default argument handling for a function.
 *
 * Example:
 * ```kotlin
 * @SwiftDefaults(generate = true)
 * fun search(query: String, limit: Int = 10, offset: Int = 0): List<Result>
 * ```
 *
 * Will generate overloads in Swift with default values.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class SwiftDefaults(
    /**
     * Whether to generate convenience overloads with default values.
     */
    val generate: Boolean = true,

    /**
     * Maximum number of default argument combinations to generate.
     */
    val maxOverloads: Int = 5
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
    val name: String
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
    val defaultValue: String = ""
)
