package io.swiftify.generator

import io.swiftify.analyzer.*
import io.swiftify.dsl.SwiftifySpec
import io.swiftify.dsl.swiftify
import io.swiftify.swift.*

/**
 * Configuration for the transformer.
 *
 * The transformer always generates complete, usable Swift code.
 * This simplified API reflects the user's mental model:
 * "I annotate my Kotlin → Swiftify generates Swift → I use it"
 */
data class TransformOptions(
    /**
     * Framework name for imports.
     */
    val frameworkName: String? = null,
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
    private val defaultsGenerator = SwiftDefaultsGenerator()
    private val asyncStreamGenerator = SwiftAsyncStreamGenerator()

    /**
     * Transform Kotlin source code to Swift with default configuration.
     */
    fun transform(kotlinSource: String): TransformResult = transform(kotlinSource, swiftify { })

    /**
     * Transform Kotlin source code to Swift with custom configuration.
     */
    fun transform(kotlinSource: String, config: SwiftifySpec): TransformResult = transform(kotlinSource, config, TransformOptions())

    /**
     * Transform Kotlin source code to Swift with transform options only (uses default DSL config).
     */
    fun transform(kotlinSource: String, options: TransformOptions): TransformResult = transform(kotlinSource, swiftify { }, options)

    /**
     * Transform Kotlin source code to Swift with full configuration.
     */
    fun transform(
        kotlinSource: String,
        config: SwiftifySpec,
        options: TransformOptions,
    ): TransformResult {
        val declarations = analyzer.analyze(kotlinSource)
        return transformDeclarations(declarations, config, options)
    }

    /**
     * Transform pre-analyzed declarations to Swift.
     *
     * Use this when declarations come from a source other than regex analysis
     * (e.g., KSP manifest parsing).
     */
    fun transformDeclarations(
        declarations: List<KotlinDeclaration>,
        options: TransformOptions,
    ): TransformResult = transformDeclarations(declarations, swiftify { }, options)

    /**
     * Transform pre-analyzed declarations to Swift with full configuration.
     *
     * Always generates complete, usable Swift code:
     * - Functions are grouped by containing class into single extension blocks
     * - Sealed classes are only transformed when annotated with @SwiftEnum
     * - Convenience overloads are generated for functions with default parameters
     */
    fun transformDeclarations(
        declarations: List<KotlinDeclaration>,
        config: SwiftifySpec,
        options: TransformOptions,
    ): TransformResult {
        val swiftCodeParts = mutableListOf<String>()
        var transformedCount = 0

        // Separate sealed classes (they don't need grouping)
        val sealedClasses = declarations.filterIsInstance<SealedClassDeclaration>()

        // Group functions by containing class for cleaner extension generation
        val suspendFunctions = declarations.filterIsInstance<FunctionDeclaration>()
        val flowFunctions = declarations.filterIsInstance<FlowFunctionDeclaration>()

        // Transform sealed classes ONLY when explicitly annotated with @SwiftEnum
        // Kotlin/Native already exports sealed classes, so we skip unannotated ones
        sealedClasses.forEach { declaration ->
            if (declaration.hasSwiftEnumAnnotation && config.defaults.transformSealedClassesToEnums) {
                val swiftCode = transformSealedClass(declaration, config)
                swiftCodeParts += swiftCode
                transformedCount++
            }
        }

        // Group functions by class and generate merged extensions
        val functionsByClass = (suspendFunctions + flowFunctions).groupBy { decl ->
            when (decl) {
                is FunctionDeclaration -> decl.containingClassName
                is FlowFunctionDeclaration -> decl.containingClassName
                else -> null
            }
        }

        functionsByClass.forEach { (className, funcs) ->
            val functionBodies = mutableListOf<String>()

            funcs.forEach { declaration ->
                when (declaration) {
                    is FunctionDeclaration -> {
                        // Check if we should process this function:
                        // - If requireAnnotations=true, only process annotated functions
                        // - If requireAnnotations=false, process all functions
                        val shouldProcess = !config.defaults.requireAnnotations || declaration.hasSwiftDefaultsAnnotation
                        if (shouldProcess && config.defaults.generateDefaultOverloads) {
                            // Since Kotlin 2.0+ generates async/await natively, we only generate:
                            // 1. Nothing for functions without default params (Kotlin has them)
                            // 2. Convenience overloads for functions WITH default params
                            val bodies = transformSuspendFunctionBodies(declaration, config)
                            if (bodies.isNotEmpty()) {
                                functionBodies += bodies
                                transformedCount++
                            }
                        }
                    }
                    is FlowFunctionDeclaration -> {
                        // Check if we should process this function:
                        // - If requireAnnotations=true, only process annotated functions
                        // - If requireAnnotations=false, process all Flow functions
                        val shouldProcess = !config.defaults.requireAnnotations || declaration.hasSwiftFlowAnnotation
                        if (shouldProcess && config.defaults.transformFlowToAsyncStream) {
                            // Flow -> AsyncStream wrappers are always needed
                            // (Kotlin only exposes raw Flow, not AsyncStream)
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

        return TransformResult(
            swiftCode = swiftCodeParts.joinToString("\n\n"),
            declarationsTransformed = transformedCount,
            declarations = declarations,
        )
    }

    private fun transformSealedClass(
        declaration: SealedClassDeclaration,
        config: SwiftifySpec,
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
                            type = mapKotlinTypeToSwift(prop.typeName, prop.isNullable),
                        )
                    }
                },
            )
        }

        val spec = SwiftEnumSpec(
            name = swiftName,
            typeParameters = declaration.typeParameters,
            cases = cases,
            conformances = conformances,
            isExhaustive = isExhaustive,
        )

        // Enums are data types - they don't need bridging implementations
        // The enum definition itself is complete
        return enumGenerator.generate(spec)
    }

    /**
     * Transform suspend function to convenience overload bodies.
     *
     * Since Kotlin 1.8+ generates async/await methods natively, we only generate
     * convenience overloads for functions WITH default parameters.
     *
     * For functions without default params, returns empty list (Kotlin already provides them).
     *
     * @return List of convenience overload bodies, or empty if none needed
     */
    private fun transformSuspendFunctionBodies(
        declaration: FunctionDeclaration,
        config: SwiftifySpec,
    ): List<String> {
        // Kotlin 2.0+ suspend functions are always async throws
        val isThrowing = declaration.isThrowing

        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue),
            )
        }

        // If no default parameters, nothing to generate
        // Kotlin already provides the full async/await method
        if (parameters.none { it.defaultValue != null }) {
            return emptyList()
        }

        val spec = SwiftDefaultsSpec(
            name = declaration.name,
            typeParameters = declaration.typeParameters,
            parameters = parameters,
            returnType = mapKotlinTypeToSwift(declaration.returnTypeName, false),
            isThrowing = isThrowing,
            isAsync = declaration.isSuspend,
        )

        // Generate convenience overload bodies (without extension wrapper)
        return defaultsGenerator.generateConvenienceOverloadBodies(spec)
    }

    /**
     * Transform flow function to just the function body (no extension wrapper).
     * Used for grouped extension generation.
     */
    private fun transformFlowFunctionBody(
        declaration: FlowFunctionDeclaration,
        config: SwiftifySpec,
    ): String {
        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = mapKotlinDefaultValueToSwift(param.defaultValue),
            )
        }

        val spec = SwiftAsyncStreamSpec(
            name = declaration.name,
            parameters = parameters,
            elementType = mapKotlinTypeToSwift(declaration.elementTypeName, false),
            isProperty = declaration.isProperty,
        )

        // Generate function body only (without extension wrapper)
        return asyncStreamGenerator.generateFunctionBody(spec)
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
                                mapKotlinTypeToSwift(types[1].trim(), false),
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
     *
     * Returns null if the default value is unknown (e.g., KSP mode only knows a default
     * exists but not what it is). This will skip generating convenience overloads for
     * parameters where we don't know the actual default value.
     */
    private fun mapKotlinDefaultValueToSwift(kotlinDefaultValue: String?): String? = when (kotlinDefaultValue) {
        null -> null
        "null" -> "nil"
        // "default" is a marker from KSP mode indicating a default exists but value is unknown
        // Skip generating overloads for these - Kotlin/Native already handles defaults
        "default" -> null
        else -> kotlinDefaultValue
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
    val declarations: List<KotlinDeclaration>,
)
