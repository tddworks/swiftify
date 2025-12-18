package io.swiftify.analyzer

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.File

/**
 * KSP Symbol Processor for Swiftify.
 *
 * This processor analyzes Kotlin code at compile time to:
 * 1. Find sealed classes for enum transformation
 * 2. Find suspend functions for async transformation
 * 3. Find Flow-returning functions for AsyncSequence transformation
 * 4. Collect annotations for configuration
 *
 * The collected declarations are output as a manifest file that the
 * linker plugin uses to generate Swift code.
 */
class SwiftifySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val outputDir = options["swiftify.outputDir"] ?: "build/swiftify"
    private val declarations = mutableListOf<KotlinDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()

        // Process sealed classes
        resolver.getAllFiles().forEach { file ->
            file.declarations.forEach { declaration ->
                if (!declaration.validate()) {
                    deferred.add(declaration)
                    return@forEach
                }

                when (declaration) {
                    is KSClassDeclaration -> processClass(declaration)
                    is KSFunctionDeclaration -> processFunction(declaration)
                    is KSPropertyDeclaration -> processProperty(declaration)
                }
            }
        }

        return deferred
    }

    override fun finish() {
        if (declarations.isEmpty()) {
            logger.info("Swiftify: No declarations found to transform")
            return
        }

        // Write manifest file
        writeManifest()
        logger.info("Swiftify: Found ${declarations.size} declarations to transform")
    }

    private fun processClass(declaration: KSClassDeclaration) {
        // Check for sealed classes
        if (declaration.modifiers.contains(Modifier.SEALED)) {
            processSealedClass(declaration)
        }
    }

    private fun processSealedClass(declaration: KSClassDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get SwiftEnum annotation if present
        val swiftEnumAnnotation = declaration.annotations.find {
            it.shortName.asString() == "SwiftEnum"
        }

        val swiftEnumName = swiftEnumAnnotation?.arguments?.find {
            it.name?.asString() == "name"
        }?.value as? String

        val exhaustive = swiftEnumAnnotation?.arguments?.find {
            it.name?.asString() == "exhaustive"
        }?.value as? Boolean ?: true

        // Get type parameters
        val typeParameters = declaration.typeParameters.map { it.name.asString() }

        // Get sealed subclasses
        val subclasses = declaration.getSealedSubclasses().map { subclass ->
            val isObject = subclass.classKind == ClassKind.OBJECT
            val properties = if (isObject) {
                emptyList()
            } else {
                subclass.primaryConstructor?.parameters?.map { param ->
                    PropertyDeclaration(
                        name = param.name?.asString() ?: "",
                        typeName = param.type.resolve().declaration.simpleName.asString(),
                        isNullable = param.type.resolve().isMarkedNullable
                    )
                } ?: emptyList()
            }

            SealedSubclass(
                simpleName = subclass.simpleName.asString(),
                qualifiedName = subclass.qualifiedName?.asString() ?: "",
                isObject = isObject,
                isDataClass = subclass.modifiers.contains(Modifier.DATA),
                properties = properties,
                typeParameters = subclass.typeParameters.map { it.name.asString() }
            )
        }.toList()

        declarations.add(
            SealedClassDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                typeParameters = typeParameters,
                subclasses = subclasses,
                hasSwiftEnumAnnotation = swiftEnumAnnotation != null,
                swiftEnumName = swiftEnumName,
                isExhaustive = exhaustive
            )
        )

        logger.info("Swiftify: Found sealed class $qualifiedName")
    }

    private fun processFunction(declaration: KSFunctionDeclaration) {
        val modifiers = declaration.modifiers

        when {
            modifiers.contains(Modifier.SUSPEND) -> processSuspendFunction(declaration)
            isFlowReturning(declaration) -> processFlowFunction(declaration)
        }
    }

    private fun processSuspendFunction(declaration: KSFunctionDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get SwiftAsync annotation if present
        val swiftAsyncAnnotation = declaration.annotations.find {
            it.shortName.asString() == "SwiftAsync"
        }

        val isThrowing = swiftAsyncAnnotation?.arguments?.find {
            it.name?.asString() == "throwing"
        }?.value as? Boolean ?: true

        // Get parameters
        val parameters = declaration.parameters.map { param ->
            ParameterDeclaration(
                name = param.name?.asString() ?: "",
                typeName = param.type.resolve().declaration.simpleName.asString(),
                isNullable = param.type.resolve().isMarkedNullable,
                defaultValue = if (param.hasDefault) "default" else null
            )
        }

        // Get return type
        val returnType = declaration.returnType?.resolve()
        val returnTypeName = returnType?.declaration?.simpleName?.asString() ?: "Unit"

        declarations.add(
            SuspendFunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = parameters,
                returnTypeName = returnTypeName,
                typeParameters = declaration.typeParameters.map { it.name.asString() },
                hasSwiftAsyncAnnotation = swiftAsyncAnnotation != null,
                isThrowing = isThrowing
            )
        )

        logger.info("Swiftify: Found suspend function $qualifiedName")
    }

    private fun processFlowFunction(declaration: KSFunctionDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get element type from Flow<T>
        val returnType = declaration.returnType?.resolve()
        val elementType = returnType?.arguments?.firstOrNull()?.type?.resolve()
        val elementTypeName = elementType?.declaration?.simpleName?.asString() ?: "Any"

        // Get parameters
        val parameters = declaration.parameters.map { param ->
            ParameterDeclaration(
                name = param.name?.asString() ?: "",
                typeName = param.type.resolve().declaration.simpleName.asString(),
                isNullable = param.type.resolve().isMarkedNullable,
                defaultValue = if (param.hasDefault) "default" else null
            )
        }

        declarations.add(
            FlowFunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = parameters,
                elementTypeName = elementTypeName
            )
        )

        logger.info("Swiftify: Found Flow function $qualifiedName")
    }

    private fun processProperty(declaration: KSPropertyDeclaration) {
        if (isFlowProperty(declaration)) {
            processFlowProperty(declaration)
        }
    }

    private fun processFlowProperty(declaration: KSPropertyDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        val returnType = declaration.type.resolve()
        val elementType = returnType.arguments.firstOrNull()?.type?.resolve()
        val elementTypeName = elementType?.declaration?.simpleName?.asString() ?: "Any"

        declarations.add(
            FlowFunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = emptyList(),
                elementTypeName = elementTypeName,
                hasSwiftFlowAnnotation = declaration.annotations.any {
                    it.shortName.asString() == "SwiftFlow"
                }
            )
        )

        logger.info("Swiftify: Found Flow property $qualifiedName")
    }

    private fun isFlowReturning(declaration: KSFunctionDeclaration): Boolean {
        val returnType = declaration.returnType?.resolve() ?: return false
        val typeName = returnType.declaration.qualifiedName?.asString() ?: return false
        return typeName in flowTypes
    }

    private fun isFlowProperty(declaration: KSPropertyDeclaration): Boolean {
        val typeName = declaration.type.resolve().declaration.qualifiedName?.asString() ?: return false
        return typeName in flowTypes
    }

    private fun writeManifest() {
        val manifest = buildString {
            appendLine("# Swiftify Declaration Manifest")
            appendLine("# Generated at compile time")
            appendLine()

            declarations.forEach { decl ->
                when (decl) {
                    is SealedClassDeclaration -> {
                        appendLine("[sealed:${decl.qualifiedName}]")
                        appendLine("name=${decl.simpleName}")
                        appendLine("package=${decl.packageName}")
                        appendLine("exhaustive=${decl.isExhaustive}")
                        if (decl.swiftEnumName != null) {
                            appendLine("swiftName=${decl.swiftEnumName}")
                        }
                        decl.subclasses.forEach { sub ->
                            appendLine("subclass=${sub.simpleName}:${sub.isObject}")
                        }
                        appendLine()
                    }
                    is SuspendFunctionDeclaration -> {
                        appendLine("[suspend:${decl.qualifiedName}]")
                        appendLine("name=${decl.name}")
                        appendLine("package=${decl.packageName}")
                        appendLine("throwing=${decl.isThrowing}")
                        appendLine("return=${decl.returnTypeName}")
                        decl.parameters.forEach { param ->
                            appendLine("param=${param.name}:${param.typeName}")
                        }
                        appendLine()
                    }
                    is FlowFunctionDeclaration -> {
                        appendLine("[flow:${decl.qualifiedName}]")
                        appendLine("name=${decl.name}")
                        appendLine("package=${decl.packageName}")
                        appendLine("element=${decl.elementTypeName}")
                        appendLine()
                    }
                }
            }
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true),
            packageName = "",
            fileName = "swiftify-manifest",
            extensionName = "txt"
        ).bufferedWriter().use { it.write(manifest) }
    }

    companion object {
        private val flowTypes = setOf(
            "kotlinx.coroutines.flow.Flow",
            "kotlinx.coroutines.flow.StateFlow",
            "kotlinx.coroutines.flow.SharedFlow",
            "kotlinx.coroutines.flow.MutableStateFlow",
            "kotlinx.coroutines.flow.MutableSharedFlow"
        )
    }
}

/**
 * Provider for the Swiftify KSP processor.
 */
class SwiftifySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SwiftifySymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
}
