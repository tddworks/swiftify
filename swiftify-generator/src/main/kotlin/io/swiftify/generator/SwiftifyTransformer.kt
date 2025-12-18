package io.swiftify.generator

import io.swiftify.analyzer.*
import io.swiftify.common.*
import io.swiftify.dsl.SwiftifySpec
import io.swiftify.dsl.swiftify

/**
 * Configuration for the transformer.
 */
data class TransformOptions(
    /**
     * Whether to generate full implementations (true) or just signatures (false).
     */
    val generateImplementations: Boolean = false,

    /**
     * The Kotlin class name for extension methods (if applicable).
     */
    val className: String? = null,

    /**
     * Framework name for imports.
     */
    val frameworkName: String? = null
)

/**
 * Main transformation engine that converts Kotlin declarations to Swift code.
 *
 * This orchestrates the full pipeline:
 * 1. Analyze Kotlin source with KotlinDeclarationAnalyzer
 * 2. Convert declarations to Swift specs
 * 3. Generate Swift code with generators
 */
class SwiftifyTransformer {

    private val analyzer = KotlinDeclarationAnalyzer()
    private val enumGenerator = SwiftEnumGenerator()
    private val asyncFunctionGenerator = SwiftAsyncFunctionGenerator()
    private val asyncSequenceGenerator = SwiftAsyncSequenceGenerator()

    /**
     * Transform Kotlin source code to Swift with default configuration.
     */
    fun transform(kotlinSource: String): TransformResult {
        return transform(kotlinSource, swiftify { })
    }

    /**
     * Transform Kotlin source code to Swift with custom configuration.
     */
    fun transform(kotlinSource: String, config: SwiftifySpec): TransformResult {
        return transform(kotlinSource, config, TransformOptions())
    }

    /**
     * Transform Kotlin source code to Swift with transform options only (uses default DSL config).
     */
    fun transform(kotlinSource: String, options: TransformOptions): TransformResult {
        return transform(kotlinSource, swiftify { }, options)
    }

    /**
     * Transform Kotlin source code to Swift with full configuration.
     */
    fun transform(
        kotlinSource: String,
        config: SwiftifySpec,
        options: TransformOptions
    ): TransformResult {
        val declarations = analyzer.analyze(kotlinSource)
        val swiftCodeParts = mutableListOf<String>()
        var transformedCount = 0

        // Separate sealed classes (they don't need grouping)
        val sealedClasses = declarations.filterIsInstance<SealedClassDeclaration>()

        // Group functions by containing class for cleaner extension generation
        val suspendFunctions = declarations.filterIsInstance<SuspendFunctionDeclaration>()
        val flowFunctions = declarations.filterIsInstance<FlowFunctionDeclaration>()

        // Transform sealed classes (only for preview mode, not for implementation)
        // When generating implementations, Kotlin/Native already exports the sealed classes
        // and we would create type conflicts by regenerating them as Swift enums
        if (!options.generateImplementations) {
            sealedClasses.forEach { declaration ->
                if (config.defaults.transformSealedClassesToEnums) {
                    val swiftCode = transformSealedClass(declaration, config, options)
                    swiftCodeParts += swiftCode
                    transformedCount++
                }
            }
        }

        // Group functions by class and generate merged extensions
        if (options.generateImplementations) {
            val functionsByClass = (suspendFunctions + flowFunctions).groupBy { decl ->
                when (decl) {
                    is SuspendFunctionDeclaration -> decl.containingClassName
                    is FlowFunctionDeclaration -> decl.containingClassName
                    else -> null
                }
            }

            functionsByClass.forEach { (className, funcs) ->
                val functionBodies = mutableListOf<String>()

                funcs.forEach { declaration ->
                    when (declaration) {
                        is SuspendFunctionDeclaration -> {
                            if (config.defaults.transformSuspendToAsync) {
                                val body = transformSuspendFunctionBody(declaration, config)
                                functionBodies += body
                                transformedCount++
                            }
                        }
                        is FlowFunctionDeclaration -> {
                            if (config.defaults.transformFlowToAsyncSequence) {
                                val body = transformFlowFunctionBody(declaration, config)
                                functionBodies += body
                                transformedCount++
                            }
                        }
                        is SealedClassDeclaration -> { /* already handled */ }
                    }
                }

                if (functionBodies.isNotEmpty()) {
                    if (className != null) {
                        // Wrap all functions in a single extension
                        val extensionCode = buildString {
                            appendLine("extension $className {")
                            append(functionBodies.joinToString("\n\n") { "    $it".replace("\n", "\n    ").trimEnd() })
                            appendLine()
                            append("}")
                        }
                        swiftCodeParts += extensionCode
                    } else {
                        // Top-level functions, no extension needed
                        swiftCodeParts += functionBodies.joinToString("\n\n")
                    }
                }
            }
        } else {
            // Signature-only mode (for preview)
            declarations.forEach { declaration ->
                when (declaration) {
                    is SuspendFunctionDeclaration -> {
                        if (config.defaults.transformSuspendToAsync) {
                            val swiftCode = transformSuspendFunction(declaration, config, options)
                            swiftCodeParts += swiftCode
                            transformedCount++
                        }
                    }
                    is FlowFunctionDeclaration -> {
                        if (config.defaults.transformFlowToAsyncSequence) {
                            val swiftCode = transformFlowFunction(declaration, config, options)
                            swiftCodeParts += swiftCode
                            transformedCount++
                        }
                    }
                    else -> { /* already handled */ }
                }
            }
        }

        return TransformResult(
            swiftCode = swiftCodeParts.joinToString("\n\n"),
            declarationsTransformed = transformedCount,
            declarations = declarations
        )
    }

    private fun transformSealedClass(
        declaration: SealedClassDeclaration,
        config: SwiftifySpec,
        options: TransformOptions
    ): String {
        // Determine configuration from annotation or DSL
        val swiftName = declaration.swiftEnumName ?: declaration.simpleName
        val isExhaustive = declaration.isExhaustive ||
                config.sealedClassRules.any { it.exhaustive }

        val conformances = buildList {
            addAll(declaration.conformances)
            config.sealedClassRules.forEach { addAll(it.conformances) }
        }.distinct()

        // Convert subclasses to enum cases
        val cases = declaration.subclasses.map { subclass ->
            SwiftEnumCase(
                name = subclass.simpleName.replaceFirstChar { it.lowercase() },
                associatedValues = if (subclass.isObject) {
                    emptyList()
                } else {
                    subclass.properties.map { prop ->
                        SwiftEnumCase.AssociatedValue(
                            label = prop.name,
                            type = mapKotlinTypeToSwift(prop.typeName, prop.isNullable)
                        )
                    }
                }
            )
        }

        val spec = SwiftEnumSpec(
            name = swiftName,
            typeParameters = declaration.typeParameters,
            cases = cases,
            conformances = conformances,
            isExhaustive = isExhaustive
        )

        // Enums are data types - they don't need bridging implementations
        // The enum definition itself is complete
        return enumGenerator.generate(spec)
    }

    private fun transformSuspendFunction(
        declaration: SuspendFunctionDeclaration,
        config: SwiftifySpec,
        options: TransformOptions
    ): String {
        val isThrowing = declaration.isThrowing ||
                config.suspendFunctionRules.any { it.throwing }

        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue)
            )
        }

        val spec = SwiftAsyncFunctionSpec(
            name = declaration.name,
            typeParameters = declaration.typeParameters,
            parameters = parameters,
            returnType = mapKotlinTypeToSwift(declaration.returnTypeName, false),
            isThrowing = isThrowing
        )

        // Use overloads if there are default parameters
        val hasDefaultParams = parameters.any { it.defaultValue != null }

        // Use the containing class name from the declaration
        val className = declaration.containingClassName

        return if (options.generateImplementations) {
            // Generate full implementations with bridging code
            if (hasDefaultParams) {
                asyncFunctionGenerator.generateWithOverloadsAndImplementation(
                    spec, className, config.defaults.maxDefaultArguments
                )
            } else {
                asyncFunctionGenerator.generateWithImplementation(spec, className)
            }
        } else {
            // Generate signatures only (for preview)
            if (hasDefaultParams) {
                asyncFunctionGenerator.generateWithOverloads(spec, config.defaults.maxDefaultArguments)
            } else {
                asyncFunctionGenerator.generate(spec)
            }
        }
    }

    private fun transformFlowFunction(
        declaration: FlowFunctionDeclaration,
        config: SwiftifySpec,
        options: TransformOptions
    ): String {
        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue)
            )
        }

        val spec = SwiftAsyncSequenceSpec(
            name = declaration.name,
            parameters = parameters,
            elementType = mapKotlinTypeToSwift(declaration.elementTypeName, false),
            isProperty = declaration.isProperty
        )

        // Use the containing class name from the declaration
        val className = declaration.containingClassName

        return if (options.generateImplementations) {
            asyncSequenceGenerator.generateWithImplementation(spec, className)
        } else {
            asyncSequenceGenerator.generate(spec)
        }
    }

    /**
     * Transform suspend function to just the function body (no extension wrapper).
     * Used for grouped extension generation.
     */
    private fun transformSuspendFunctionBody(
        declaration: SuspendFunctionDeclaration,
        config: SwiftifySpec
    ): String {
        val isThrowing = declaration.isThrowing ||
                config.suspendFunctionRules.any { it.throwing }

        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue)
            )
        }

        val spec = SwiftAsyncFunctionSpec(
            name = declaration.name,
            typeParameters = declaration.typeParameters,
            parameters = parameters,
            returnType = mapKotlinTypeToSwift(declaration.returnTypeName, false),
            isThrowing = isThrowing
        )

        // Generate function body only (without extension wrapper)
        return asyncFunctionGenerator.generateFunctionBody(spec)
    }

    /**
     * Transform flow function to just the function body (no extension wrapper).
     * Used for grouped extension generation.
     */
    private fun transformFlowFunctionBody(
        declaration: FlowFunctionDeclaration,
        config: SwiftifySpec
    ): String {
        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue)
            )
        }

        val spec = SwiftAsyncSequenceSpec(
            name = declaration.name,
            parameters = parameters,
            elementType = mapKotlinTypeToSwift(declaration.elementTypeName, false),
            isProperty = declaration.isProperty
        )

        // Generate function body only (without extension wrapper)
        return asyncSequenceGenerator.generateFunctionBody(spec)
    }

    private fun mapKotlinTypeToSwift(kotlinType: String, isNullable: Boolean): SwiftType {
        val baseType = when (kotlinType) {
            "String" -> SwiftType.Named("String")
            // Kotlin Int exports as Int32 in Kotlin/Native Swift interop
            "Int" -> SwiftType.Named("Int32")
            "Long" -> SwiftType.Named("Int64")
            "Float" -> SwiftType.Named("Float")
            "Double" -> SwiftType.Named("Double")
            "Boolean", "Bool" -> SwiftType.Named("Bool")
            "ByteArray" -> SwiftType.Named("Data")
            "Throwable", "Exception" -> SwiftType.Named("Error")
            "Unit" -> SwiftType.Void
            else -> {
                // Handle generic types like List<T>, Map<K,V>
                when {
                    kotlinType.startsWith("List<") -> {
                        val elementType = kotlinType.removePrefix("List<").removeSuffix(">")
                        SwiftType.Array(mapKotlinTypeToSwift(elementType, false))
                    }
                    kotlinType.startsWith("Map<") -> {
                        val types = kotlinType.removePrefix("Map<").removeSuffix(">").split(",")
                        if (types.size == 2) {
                            SwiftType.Dictionary(
                                mapKotlinTypeToSwift(types[0].trim(), false),
                                mapKotlinTypeToSwift(types[1].trim(), false)
                            )
                        } else {
                            SwiftType.Named(kotlinType)
                        }
                    }
                    kotlinType.matches(Regex("[A-Z]")) -> SwiftType.Generic(kotlinType)
                    else -> SwiftType.Named(kotlinType)
                }
            }
        }

        return if (isNullable) SwiftType.Optional(baseType) else baseType
    }

    /**
     * Convert Kotlin default value to Swift.
     */
    private fun mapKotlinDefaultValueToSwift(kotlinDefaultValue: String?): String? {
        return when (kotlinDefaultValue) {
            null -> null
            "null" -> "nil"
            else -> kotlinDefaultValue
        }
    }
}

/**
 * Result of a transformation operation.
 */
data class TransformResult(
    /**
     * The generated Swift source code.
     */
    val swiftCode: String,

    /**
     * Number of declarations that were transformed.
     */
    val declarationsTransformed: Int,

    /**
     * The analyzed declarations.
     */
    val declarations: List<KotlinDeclaration>
)
