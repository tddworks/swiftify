package io.swiftify.analyzer

/**
 * Analyzes Kotlin source code to extract declarations for Swift transformation.
 *
 * This is a simple source-based analyzer for testing and preview purposes.
 * In production, KSP-based analysis provides more accurate results.
 */
class KotlinDeclarationAnalyzer {

    private val packagePattern = Regex("""package\s+([\w.]+)""")
    private val sealedClassPattern = Regex(
        """(@SwiftEnum\s*\([^)]*\)\s*)?sealed\s+class\s+(\w+)(?:<([^>]+)>)?"""
    )
    private val dataClassPattern = Regex(
        """data\s+class\s+(\w+)(?:<([^>]+)>)?\s*\(([^)]*)\)\s*:\s*(\w+)(?:<[^>]*>)?(?:\(\))?"""
    )
    private val objectPattern = Regex(
        """(?:data\s+)?object\s+(\w+)\s*:\s*(\w+)(?:<[^>]*>)?(?:\(\))?"""
    )
    private val suspendFunctionPattern = Regex(
        """(@SwiftAsync\s*\([^)]*\)\s*)?suspend\s+fun\s+(\w+)(?:<([^>]+)>)?\s*\(([^)]*)\)(?:\s*:\s*(\S+))?"""
    )
    private val flowFunctionPattern = Regex(
        """fun\s+(\w+)\s*\(([^)]*)\)\s*:\s*(?:Flow|StateFlow|SharedFlow)<(\w+\??)>"""
    )
    private val flowPropertyPattern = Regex(
        """val\s+(\w+)\s*:\s*(?:Flow|StateFlow|SharedFlow)<(\w+\??)>"""
    )
    private val propertyPattern = Regex(
        """val\s+(\w+)\s*:\s*(\w+\??(?:<[^>]+>)?)"""
    )
    private val parameterPattern = Regex(
        """(\w+)\s*:\s*(\w+\??(?:<[^>]+>)?)(?:\s*=\s*(\d+|"[^"]*"|true|false|\w+))?"""
    )
    private val swiftEnumAnnotationPattern = Regex(
        """@SwiftEnum\s*\(\s*name\s*=\s*"(\w+)"(?:,\s*exhaustive\s*=\s*(true|false))?\s*\)"""
    )
    private val swiftAsyncAnnotationPattern = Regex(
        """@SwiftAsync\s*\(\s*throwing\s*=\s*(true|false)\s*\)"""
    )

    /**
     * Analyze Kotlin source code and extract transformable declarations.
     */
    fun analyze(source: String): List<KotlinDeclaration> {
        val declarations = mutableListOf<KotlinDeclaration>()

        // Strip comments to avoid false matches
        val cleanedSource = stripComments(source)

        val packageName = packagePattern.find(source)?.groupValues?.get(1) ?: ""

        // Analyze sealed classes
        declarations += analyzeSealedClasses(cleanedSource, packageName)

        // Analyze suspend functions
        declarations += analyzeSuspendFunctions(cleanedSource, packageName)

        // Analyze Flow-returning functions
        declarations += analyzeFlowFunctions(cleanedSource, packageName)

        return declarations
    }

    /**
     * Strip single-line and multi-line comments from source code.
     */
    private fun stripComments(source: String): String {
        // Remove multi-line comments (/** ... */ and /* ... */)
        val withoutBlockComments = source.replace(Regex("""/\*[\s\S]*?\*/"""), "")
        // Remove single-line comments (// ...)
        return withoutBlockComments.replace(Regex("""//.*$""", RegexOption.MULTILINE), "")
    }

    private fun analyzeSealedClasses(source: String, packageName: String): List<SealedClassDeclaration> {
        val results = mutableListOf<SealedClassDeclaration>()

        sealedClassPattern.findAll(source).forEach { match ->
            val annotation = match.groupValues[1]
            val className = match.groupValues[2]
            val typeParams = match.groupValues[3]
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.map { it.trim() }
                ?: emptyList()

            // Parse annotation if present
            val swiftEnumMatch = swiftEnumAnnotationPattern.find(annotation)
            val hasAnnotation = swiftEnumMatch != null
            val swiftName = swiftEnumMatch?.groupValues?.get(1)
            val exhaustive = swiftEnumMatch?.groupValues?.get(2)?.toBooleanStrictOrNull() ?: true

            // Find subclasses
            val subclasses = findSubclasses(source, className)

            results += SealedClassDeclaration(
                qualifiedName = if (packageName.isNotEmpty()) "$packageName.$className" else className,
                simpleName = className,
                packageName = packageName,
                typeParameters = typeParams,
                subclasses = subclasses,
                hasSwiftEnumAnnotation = hasAnnotation,
                swiftEnumName = swiftName,
                isExhaustive = exhaustive
            )
        }

        return results
    }

    private fun findSubclasses(source: String, parentClass: String): List<SealedSubclass> {
        val subclasses = mutableListOf<SealedSubclass>()

        // Find data class subclasses
        dataClassPattern.findAll(source).forEach { match ->
            val className = match.groupValues[1]
            val typeParams = match.groupValues[2]
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.map { it.trim() }
                ?: emptyList()
            val propsStr = match.groupValues[3]
            val parent = match.groupValues[4]

            if (parent == parentClass || parent.startsWith(parentClass)) {
                val properties = parseProperties(propsStr)
                subclasses += SealedSubclass(
                    simpleName = className,
                    qualifiedName = className,
                    isObject = false,
                    isDataClass = true,
                    properties = properties,
                    typeParameters = typeParams
                )
            }
        }

        // Find object subclasses
        objectPattern.findAll(source).forEach { match ->
            val objectName = match.groupValues[1]
            val parent = match.groupValues[2]

            if (parent == parentClass || parent.startsWith(parentClass)) {
                subclasses += SealedSubclass(
                    simpleName = objectName,
                    qualifiedName = objectName,
                    isObject = true,
                    isDataClass = false,
                    properties = emptyList()
                )
            }
        }

        return subclasses
    }

    private fun parseProperties(propsStr: String): List<PropertyDeclaration> {
        if (propsStr.isBlank()) return emptyList()

        return propertyPattern.findAll(propsStr).map { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            PropertyDeclaration(
                name = name,
                typeName = type.removeSuffix("?"),
                isNullable = type.endsWith("?")
            )
        }.toList()
    }

    private fun analyzeSuspendFunctions(source: String, packageName: String): List<SuspendFunctionDeclaration> {
        val results = mutableListOf<SuspendFunctionDeclaration>()

        suspendFunctionPattern.findAll(source).forEach { match ->
            val annotation = match.groupValues[1]
            val funcName = match.groupValues[2]
            val typeParams = match.groupValues[3]
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.map { it.trim() }
                ?: emptyList()
            val paramsStr = match.groupValues[4]
            // Return type is optional - if not specified, it's Unit
            val returnType = match.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "Unit"

            // Parse annotation if present
            val swiftAsyncMatch = swiftAsyncAnnotationPattern.find(annotation)
            val hasAnnotation = swiftAsyncMatch != null || annotation.contains("@SwiftAsync")
            val isThrowing = swiftAsyncMatch?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true

            val parameters = parseParameters(paramsStr)

            results += SuspendFunctionDeclaration(
                qualifiedName = if (packageName.isNotEmpty()) "$packageName.$funcName" else funcName,
                simpleName = funcName,
                packageName = packageName,
                name = funcName,
                parameters = parameters,
                returnTypeName = returnType,
                typeParameters = typeParams,
                hasSwiftAsyncAnnotation = hasAnnotation,
                isThrowing = isThrowing
            )
        }

        return results
    }

    private fun parseParameters(paramsStr: String): List<ParameterDeclaration> {
        if (paramsStr.isBlank()) return emptyList()

        return parameterPattern.findAll(paramsStr).map { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            val defaultValue = match.groupValues[3].takeIf { it.isNotBlank() }

            ParameterDeclaration(
                name = name,
                typeName = type.removeSuffix("?"),
                isNullable = type.endsWith("?"),
                defaultValue = defaultValue
            )
        }.toList()
    }

    private fun analyzeFlowFunctions(source: String, packageName: String): List<FlowFunctionDeclaration> {
        val results = mutableListOf<FlowFunctionDeclaration>()

        // Analyze Flow-returning functions
        flowFunctionPattern.findAll(source).forEach { match ->
            val funcName = match.groupValues[1]
            val paramsStr = match.groupValues[2]
            val elementType = match.groupValues[3]

            val parameters = parseParameters(paramsStr)

            results += FlowFunctionDeclaration(
                qualifiedName = if (packageName.isNotEmpty()) "$packageName.$funcName" else funcName,
                simpleName = funcName,
                packageName = packageName,
                name = funcName,
                parameters = parameters,
                elementTypeName = elementType.removeSuffix("?"),
                isProperty = false
            )
        }

        // Analyze Flow properties
        flowPropertyPattern.findAll(source).forEach { match ->
            val propName = match.groupValues[1]
            val elementType = match.groupValues[2]

            results += FlowFunctionDeclaration(
                qualifiedName = if (packageName.isNotEmpty()) "$packageName.$propName" else propName,
                simpleName = propName,
                packageName = packageName,
                name = propName,
                parameters = emptyList(),
                elementTypeName = elementType.removeSuffix("?"),
                isProperty = true
            )
        }

        return results
    }
}
