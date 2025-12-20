package io.swiftify.analyzer

/**
 * Interface for providing Kotlin declarations from different analysis sources.
 *
 * This interface abstracts the source of declarations, allowing both:
 * - REGEX-based source code analysis
 * - KSP-based manifest analysis
 *
 * Usage:
 * ```kotlin
 * // For REGEX mode
 * val provider = RegexDeclarationProvider(sourceFiles)
 * val declarations = provider.getDeclarations()
 *
 * // For KSP mode
 * val provider = KspDeclarationProvider(manifestFiles)
 * val declarations = provider.getDeclarations()
 * ```
 */
interface DeclarationProvider {
    /**
     * Get all Kotlin declarations from the source.
     *
     * @return List of declarations that can be transformed to Swift
     */
    fun getDeclarations(): List<KotlinDeclaration>

    /**
     * Check if the provider has any valid input to analyze.
     *
     * @return true if there are valid sources/manifests to process
     */
    fun hasValidInput(): Boolean

    /**
     * Get a human-readable description of the source (for logging).
     */
    fun getSourceDescription(): String
}
