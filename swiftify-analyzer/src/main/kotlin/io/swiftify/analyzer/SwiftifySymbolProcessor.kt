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
    private val options: Map<String, String>,
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
                    is KSFunctionDeclaration -> processTopLevelFunction(declaration)
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

        // Process member functions and properties inside the class
        val className = declaration.simpleName.asString()
        declaration.getAllFunctions().forEach { function ->
            processMemberFunction(function, className)
        }
        declaration.getAllProperties().forEach { property ->
            processMemberProperty(property, className)
        }
    }

    private fun processMemberFunction(
        declaration: KSFunctionDeclaration,
        containingClassName: String,
    ) {
        val modifiers = declaration.modifiers

        // Check for @SwiftDefaults annotation
        val hasSwiftDefaults =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftDefaults"
            }

        val isSuspend = modifiers.contains(Modifier.SUSPEND)

        // Process functions with @SwiftDefaults annotation
        if (hasSwiftDefaults) {
            processFunction(declaration, containingClassName, isSuspend)
        }

        // Check for @SwiftFlow on Flow-returning functions
        val hasSwiftFlow =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftFlow"
            }

        if (hasSwiftFlow && isFlowReturning(declaration)) {
            processFlowFunction(declaration, containingClassName)
        }
    }

    private fun processMemberProperty(
        declaration: KSPropertyDeclaration,
        containingClassName: String,
    ) {
        // Check for @SwiftFlow annotation on properties
        val hasSwiftFlow =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftFlow"
            }

        if (hasSwiftFlow) {
            processFlowProperty(declaration, containingClassName)
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
                    val type = param.type.resolve()
                    PropertyDeclaration(
                        name = param.name?.asString() ?: "",
                        typeName = getFullTypeName(type),
                        isNullable = type.isMarkedNullable,
                    )
                } ?: emptyList()
            }

            SealedSubclass(
                simpleName = subclass.simpleName.asString(),
                qualifiedName = subclass.qualifiedName?.asString() ?: "",
                isObject = isObject,
                isDataClass = subclass.modifiers.contains(Modifier.DATA),
                properties = properties,
                typeParameters = subclass.typeParameters.map { it.name.asString() },
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
                isExhaustive = exhaustive,
            ),
        )

        logger.info("Swiftify: Found sealed class $qualifiedName")
    }

    private fun processTopLevelFunction(declaration: KSFunctionDeclaration) {
        val modifiers = declaration.modifiers

        // Check for annotations
        val hasSwiftDefaults =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftDefaults"
            }
        val hasSwiftFlow =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftFlow"
            }

        val isSuspend = modifiers.contains(Modifier.SUSPEND)

        when {
            hasSwiftDefaults -> processFunction(declaration, null, isSuspend)
            hasSwiftFlow && isFlowReturning(declaration) -> processFlowFunction(declaration, null)
        }
    }

    private fun processFunction(
        declaration: KSFunctionDeclaration,
        containingClassName: String?,
        isSuspend: Boolean,
    ) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get parameters with full type names and extracted default values
        val parameters =
            declaration.parameters.map { param ->
                val type = param.type.resolve()
                ParameterDeclaration(
                    name = param.name?.asString() ?: "",
                    typeName = getFullTypeName(type),
                    isNullable = type.isMarkedNullable,
                    defaultValue = extractDefaultValue(param),
                )
            }

        // Get return type with full generic info
        val returnType = declaration.returnType?.resolve()
        val returnTypeName = if (returnType != null) getFullTypeName(returnType) else "Unit"

        declarations.add(
            FunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = parameters,
                returnTypeName = returnTypeName,
                typeParameters = declaration.typeParameters.map { it.name.asString() },
                hasSwiftDefaultsAnnotation = true,
                isThrowing = isSuspend, // Suspend functions are throwing by default
                containingClassName = containingClassName,
                isSuspend = isSuspend,
            ),
        )

        val functionType = if (isSuspend) "suspend" else "regular"
        logger.info("Swiftify: Found $functionType function $qualifiedName")
    }

    private fun processFlowFunction(
        declaration: KSFunctionDeclaration,
        containingClassName: String?,
    ) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get element type from Flow<T> with full generic info
        val returnType = declaration.returnType?.resolve()
        val elementType = returnType?.arguments?.firstOrNull()?.type?.resolve()
        val elementTypeName = if (elementType != null) getFullTypeName(elementType) else "Any"

        // Get parameters with full type names and extracted default values
        val parameters =
            declaration.parameters.map { param ->
                val type = param.type.resolve()
                ParameterDeclaration(
                    name = param.name?.asString() ?: "",
                    typeName = getFullTypeName(type),
                    isNullable = type.isMarkedNullable,
                    defaultValue = extractDefaultValue(param),
                )
            }

        val hasSwiftFlow =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftFlow"
            }

        declarations.add(
            FlowFunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = parameters,
                elementTypeName = elementTypeName,
                hasSwiftFlowAnnotation = hasSwiftFlow,
                containingClassName = containingClassName,
            ),
        )

        logger.info("Swiftify: Found Flow function $qualifiedName")
    }

    private fun processProperty(declaration: KSPropertyDeclaration) {
        // Check for @SwiftFlow annotation
        val hasSwiftFlow =
            declaration.annotations.any {
                it.shortName.asString() == "SwiftFlow"
            }
        if (hasSwiftFlow && isFlowProperty(declaration)) {
            processFlowProperty(declaration, null)
        }
    }

    private fun processFlowProperty(
        declaration: KSPropertyDeclaration,
        containingClassName: String?,
    ) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        val simpleName = declaration.simpleName.asString()
        val packageName = declaration.packageName.asString()

        // Get element type from Flow<T> with full generic info
        val returnType = declaration.type.resolve()
        val elementType = returnType.arguments.firstOrNull()?.type?.resolve()
        val elementTypeName = if (elementType != null) getFullTypeName(elementType) else "Any"

        declarations.add(
            FlowFunctionDeclaration(
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                packageName = packageName,
                name = simpleName,
                parameters = emptyList(),
                elementTypeName = elementTypeName,
                hasSwiftFlowAnnotation = true,
                isProperty = true,
                containingClassName = containingClassName,
            ),
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
                        if (decl.typeParameters.isNotEmpty()) {
                            appendLine("typeParams=${decl.typeParameters.joinToString(",")}")
                        }
                        decl.subclasses.forEach { sub ->
                            // Format: subclass=name|property1:type1,property2:type2|isObject
                            val propsStr = sub.properties.joinToString(",") { prop ->
                                "${prop.name}:${prop.typeName}${if (prop.isNullable) "?" else ""}"
                            }
                            appendLine("subclass=${sub.simpleName}|$propsStr|${sub.isObject}")
                        }
                        appendLine()
                    }
                    is FunctionDeclaration -> {
                        appendLine("[suspend:${decl.qualifiedName}]")
                        appendLine("name=${decl.name}")
                        appendLine("package=${decl.packageName}")
                        appendLine("throwing=${decl.isThrowing}")
                        appendLine("return=${decl.returnTypeName}")
                        if (decl.containingClassName != null) {
                            appendLine("class=${decl.containingClassName}")
                        }
                        if (decl.hasSwiftDefaultsAnnotation) {
                            appendLine("hasAnnotation=true")
                        }
                        decl.parameters.forEach { param ->
                            val defaultSuffix = if (param.defaultValue != null) "=${param.defaultValue}" else ""
                            appendLine("param=${param.name}:${param.typeName}$defaultSuffix")
                        }
                        appendLine()
                    }
                    is FlowFunctionDeclaration -> {
                        appendLine("[flow:${decl.qualifiedName}]")
                        appendLine("name=${decl.name}")
                        appendLine("package=${decl.packageName}")
                        appendLine("element=${decl.elementTypeName}")
                        if (decl.containingClassName != null) {
                            appendLine("class=${decl.containingClassName}")
                        }
                        if (decl.isProperty) {
                            appendLine("isProperty=true")
                        }
                        if (decl.hasSwiftFlowAnnotation) {
                            appendLine("hasAnnotation=true")
                        }
                        decl.parameters.forEach { param ->
                            appendLine("param=${param.name}:${param.typeName}")
                        }
                        appendLine()
                    }
                }
            }
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true),
            packageName = "",
            fileName = "swiftify-manifest",
            extensionName = "txt",
        ).bufferedWriter().use { it.write(manifest) }
    }

    /**
     * Get the full type name including generic arguments.
     * For example: List<String>, Map<String, Int>, etc.
     */
    private fun getFullTypeName(type: KSType): String {
        val baseName = type.declaration.simpleName.asString()
        val typeArgs = type.arguments
        return if (typeArgs.isEmpty()) {
            baseName
        } else {
            val argsStr = typeArgs.joinToString(", ") { arg ->
                val argType = arg.type?.resolve()
                if (argType != null) {
                    getFullTypeName(argType)
                } else {
                    "Any"
                }
            }
            "$baseName<$argsStr>"
        }
    }

    /**
     * Extract default value expression from source text if possible.
     * This uses a regex-based approach since KSP doesn't directly expose default values.
     */
    private fun extractDefaultValue(param: KSValueParameter): String? {
        if (!param.hasDefault) return null

        // Try to get source text from the containing declaration
        val containingDecl = param.parent as? KSFunctionDeclaration ?: return "default"

        try {
            // Get the source text of the function
            val location = containingDecl.location as? com.google.devtools.ksp.symbol.FileLocation
                ?: return "default"

            val file = java.io.File(location.filePath)
            if (!file.exists()) return "default"

            val source = file.readText()
            val paramName = param.name?.asString() ?: return "default"

            // Pattern to match "paramName: Type = value" across multiple lines
            // Handles: page: Int = 1, or includeArchived: Boolean = false,
            // Type pattern: word chars, optionally followed by <generic>, optionally nullable
            val pattern = Regex(
                """$paramName\s*:\s*(\w+(?:<[^>]+>)?)\??\s*=\s*([^\n,)]+)""",
                RegexOption.MULTILINE,
            )
            val match = pattern.find(source)

            if (match != null) {
                // groupValues[1] is the type, groupValues[2] is the value
                val value = match.groupValues[2].trim()
                // Validate it's a simple literal value we can use in Swift
                return when {
                    value == "null" -> "null"
                    value == "true" || value == "false" -> value
                    value.matches(Regex("""-?\d+(\.\d+)?""")) -> value
                    value.startsWith("\"") && value.endsWith("\"") -> value
                    value.matches(Regex("""\w+\.\w+""")) -> value // Enum values like Color.RED
                    else -> "default" // Complex expression, can't safely extract
                }
            }
        } catch (e: Exception) {
            logger.warn("Swiftify: Could not extract default value for $param: ${e.message}")
        }

        return "default"
    }

    companion object {
        private val flowTypes = setOf(
            "kotlinx.coroutines.flow.Flow",
            "kotlinx.coroutines.flow.StateFlow",
            "kotlinx.coroutines.flow.SharedFlow",
            "kotlinx.coroutines.flow.MutableStateFlow",
            "kotlinx.coroutines.flow.MutableSharedFlow",
        )
    }
}

/**
 * Provider for the Swiftify KSP processor.
 */
class SwiftifySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = SwiftifySymbolProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
        options = environment.options,
    )
}
