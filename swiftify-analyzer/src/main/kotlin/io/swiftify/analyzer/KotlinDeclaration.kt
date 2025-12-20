package io.swiftify.analyzer

/**
 * Base interface for Kotlin declarations that can be transformed to Swift.
 */
sealed interface KotlinDeclaration {
    val qualifiedName: String
    val simpleName: String
    val packageName: String
}

/**
 * Represents a sealed class declaration that can be transformed to a Swift enum.
 */
data class SealedClassDeclaration(
    override val qualifiedName: String,
    override val simpleName: String,
    override val packageName: String,
    val typeParameters: List<String>,
    val subclasses: List<SealedSubclass>,
    val hasSwiftEnumAnnotation: Boolean = false,
    val swiftEnumName: String? = null,
    val isExhaustive: Boolean = true,
    val conformances: List<String> = emptyList(),
) : KotlinDeclaration

/**
 * Represents a subclass of a sealed class.
 */
data class SealedSubclass(
    val simpleName: String,
    val qualifiedName: String,
    val isObject: Boolean,
    val isDataClass: Boolean,
    val properties: List<PropertyDeclaration>,
    val typeParameters: List<String> = emptyList(),
)

/**
 * Represents a property in a data class or class.
 */
data class PropertyDeclaration(
    val name: String,
    val typeName: String,
    val isNullable: Boolean = false,
)

/**
 * Represents a function that can be transformed to Swift.
 * Functions with @SwiftDefaults generate convenience overloads for default parameters.
 * Suspend functions are exported as async by Kotlin 2.0+ automatically.
 */
data class FunctionDeclaration(
    override val qualifiedName: String,
    override val simpleName: String,
    override val packageName: String,
    val name: String,
    val parameters: List<ParameterDeclaration>,
    val returnTypeName: String,
    val typeParameters: List<String> = emptyList(),
    /** Whether this function has the @SwiftDefaults annotation */
    val hasSwiftDefaultsAnnotation: Boolean = false,
    val isThrowing: Boolean = true,
    val receiverTypeName: String? = null,
    /** The name of the class containing this function, if any */
    val containingClassName: String? = null,
    /** Whether this is a suspend function (generates async in Swift) */
    val isSuspend: Boolean = true,
) : KotlinDeclaration

/**
 * Represents a function parameter.
 */
data class ParameterDeclaration(
    val name: String,
    val typeName: String,
    val isNullable: Boolean = false,
    val defaultValue: String? = null,
    val isVararg: Boolean = false,
)

/**
 * Represents a function returning Flow that can be transformed to AsyncSequence.
 */
data class FlowFunctionDeclaration(
    override val qualifiedName: String,
    override val simpleName: String,
    override val packageName: String,
    val name: String,
    val parameters: List<ParameterDeclaration>,
    val elementTypeName: String,
    val hasSwiftFlowAnnotation: Boolean = false,
    val isProperty: Boolean = false,
    /** The name of the class containing this function/property, if any */
    val containingClassName: String? = null,
) : KotlinDeclaration
