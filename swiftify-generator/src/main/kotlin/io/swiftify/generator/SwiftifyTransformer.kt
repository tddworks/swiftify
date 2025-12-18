package io.swiftify.generator

import io.swiftify.analyzer.*
import io.swiftify.common.*
import io.swiftify.dsl.SwiftifySpec
import io.swiftify.dsl.swiftify

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
        val declarations = analyzer.analyze(kotlinSource)
        val swiftCodeParts = mutableListOf<String>()
        var transformedCount = 0

        declarations.forEach { declaration ->
            when (declaration) {
                is SealedClassDeclaration -> {
                    if (config.defaults.transformSealedClassesToEnums) {
                        val swiftCode = transformSealedClass(declaration, config)
                        swiftCodeParts += swiftCode
                        transformedCount++
                    }
                }
                is SuspendFunctionDeclaration -> {
                    if (config.defaults.transformSuspendToAsync) {
                        val swiftCode = transformSuspendFunction(declaration, config)
                        swiftCodeParts += swiftCode
                        transformedCount++
                    }
                }
                is FlowFunctionDeclaration -> {
                    if (config.defaults.transformFlowToAsyncSequence) {
                        // TODO: Implement Flow transformation
                        transformedCount++
                    }
                }
            }
        }

        return TransformResult(
            swiftCode = swiftCodeParts.joinToString("\n\n"),
            declarationsTransformed = transformedCount,
            declarations = declarations
        )
    }

    private fun transformSealedClass(declaration: SealedClassDeclaration, config: SwiftifySpec): String {
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

        return enumGenerator.generate(spec)
    }

    private fun transformSuspendFunction(declaration: SuspendFunctionDeclaration, config: SwiftifySpec): String {
        val isThrowing = declaration.isThrowing ||
                config.suspendFunctionRules.any { it.throwing }

        val parameters = declaration.parameters.map { param ->
            SwiftParameter(
                name = param.name,
                type = mapKotlinTypeToSwift(param.typeName, param.isNullable),
                defaultValue = param.defaultValue
            )
        }

        val spec = SwiftAsyncFunctionSpec(
            name = declaration.name,
            typeParameters = declaration.typeParameters,
            parameters = parameters,
            returnType = mapKotlinTypeToSwift(declaration.returnTypeName, false),
            isThrowing = isThrowing
        )

        return asyncFunctionGenerator.generate(spec)
    }

    private fun mapKotlinTypeToSwift(kotlinType: String, isNullable: Boolean): SwiftType {
        val baseType = when (kotlinType) {
            "String" -> SwiftType.Named("String")
            "Int" -> SwiftType.Named("Int")
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
