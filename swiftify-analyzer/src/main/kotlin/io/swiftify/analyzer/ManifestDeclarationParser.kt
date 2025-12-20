package io.swiftify.analyzer

/**
 * Parses a Swiftify manifest file and reconstructs KotlinDeclaration objects.
 *
 * The manifest format is:
 * ```
 * [sealed:com.example.NetworkResult]
 * name=NetworkResult
 * package=com.example
 * exhaustive=true
 * typeParams=T
 * subclass=Success|data:T|false
 * subclass=Error|message:String,code:Int|false
 * subclass=Loading||true
 * ```
 */
class ManifestDeclarationParser {
    /**
     * Parse manifest content and return a list of KotlinDeclaration objects.
     */
    fun parse(content: String): List<KotlinDeclaration> {
        val declarations = mutableListOf<KotlinDeclaration>()
        var currentSection: MutableMap<String, String>? = null
        var currentType: String? = null
        val subclasses = mutableListOf<SubclassInfo>()
        val parameters = mutableListOf<Pair<String, String>>()

        content.lines().forEach { line ->
            when {
                line.startsWith("[sealed:") -> {
                    saveCurrentDeclaration(currentType, currentSection, subclasses, parameters, declarations)
                    currentType = "sealed"
                    val qualifiedName = line.substringAfter("[sealed:").substringBefore("]")
                    currentSection = mutableMapOf("qualifiedName" to qualifiedName)
                    subclasses.clear()
                }
                line.startsWith("[suspend:") -> {
                    saveCurrentDeclaration(currentType, currentSection, subclasses, parameters, declarations)
                    currentType = "suspend"
                    val qualifiedName = line.substringAfter("[suspend:").substringBefore("]")
                    currentSection = mutableMapOf("qualifiedName" to qualifiedName)
                    parameters.clear()
                }
                line.startsWith("[flow:") -> {
                    saveCurrentDeclaration(currentType, currentSection, subclasses, parameters, declarations)
                    currentType = "flow"
                    val qualifiedName = line.substringAfter("[flow:").substringBefore("]")
                    currentSection = mutableMapOf("qualifiedName" to qualifiedName)
                    parameters.clear()
                }
                line.startsWith("subclass=") -> {
                    // Format: subclass=name|property1:type1,property2:type2|isObject
                    val value = line.substringAfter("subclass=")
                    val parts = value.split("|")
                    if (parts.size >= 3) {
                        val name = parts[0]
                        val propsStr = parts[1]
                        val isObject = parts[2].toBooleanStrictOrNull() ?: false

                        val properties =
                            if (propsStr.isBlank()) {
                                emptyList()
                            } else {
                                propsStr.split(",").mapNotNull { propStr ->
                                    val propParts = propStr.split(":")
                                    if (propParts.size >= 2) {
                                        val propName = propParts[0]
                                        val propType = propParts[1].removeSuffix("?")
                                        val isNullable = propParts[1].endsWith("?")
                                        PropertyDeclaration(propName, propType, isNullable)
                                    } else {
                                        null
                                    }
                                }
                            }

                        subclasses.add(SubclassInfo(name, properties, isObject))
                    }
                }
                line.startsWith("param=") -> {
                    val parts = line.substringAfter("param=").split(":")
                    if (parts.size >= 2) {
                        parameters.add(parts[0] to parts[1])
                    }
                }
                line.contains("=") && currentSection != null && !line.startsWith("#") -> {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        currentSection!![parts[0]] = parts[1]
                    }
                }
            }
        }

        saveCurrentDeclaration(currentType, currentSection, subclasses, parameters, declarations)
        return declarations
    }

    private fun saveCurrentDeclaration(
        type: String?,
        section: Map<String, String>?,
        subclasses: List<SubclassInfo>,
        parameters: List<Pair<String, String>>,
        declarations: MutableList<KotlinDeclaration>,
    ) {
        if (type == null || section == null) return

        when (type) {
            "sealed" -> {
                val qualifiedName = section["qualifiedName"] ?: return
                val simpleName = section["name"] ?: qualifiedName.substringAfterLast(".")
                val packageName = section["package"] ?: qualifiedName.substringBeforeLast(".", "")
                val typeParams =
                    section["typeParams"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                        ?: emptyList()

                val sealedSubclasses =
                    subclasses.map { info ->
                        SealedSubclass(
                            simpleName = info.name,
                            qualifiedName = "$packageName.${info.name}",
                            isObject = info.isObject,
                            isDataClass = !info.isObject,
                            properties = info.properties,
                            typeParameters = emptyList(),
                        )
                    }

                declarations.add(
                    SealedClassDeclaration(
                        qualifiedName = qualifiedName,
                        simpleName = simpleName,
                        packageName = packageName,
                        typeParameters = typeParams,
                        subclasses = sealedSubclasses,
                        hasSwiftEnumAnnotation = section["swiftName"] != null,
                        swiftEnumName = section["swiftName"],
                        isExhaustive = section["exhaustive"]?.toBooleanStrictOrNull() ?: true,
                    ),
                )
            }
            "suspend" -> {
                val qualifiedName = section["qualifiedName"] ?: return
                val name = section["name"] ?: qualifiedName.substringAfterLast(".")
                val packageName = section["package"] ?: ""
                val containingClass = section["class"]
                val hasAnnotation = section["hasAnnotation"]?.toBooleanStrictOrNull() ?: false

                val params =
                    parameters.map { (paramName, typeInfo) ->
                        // typeInfo can be "Int", "Int=1", "Boolean=false", "String?=null"
                        val (typeName, defaultValue) = if (typeInfo.contains("=")) {
                            val parts = typeInfo.split("=", limit = 2)
                            parts[0] to parts[1]
                        } else {
                            typeInfo to null
                        }
                        ParameterDeclaration(
                            name = paramName,
                            typeName = typeName.removeSuffix("?"),
                            isNullable = typeName.endsWith("?"),
                            defaultValue = defaultValue,
                        )
                    }

                declarations.add(
                    FunctionDeclaration(
                        qualifiedName = qualifiedName,
                        simpleName = name,
                        packageName = packageName,
                        name = name,
                        parameters = params,
                        returnTypeName = section["return"] ?: "Unit",
                        isThrowing = section["throwing"]?.toBooleanStrictOrNull() ?: true,
                        hasSwiftDefaultsAnnotation = hasAnnotation,
                        containingClassName = containingClass,
                    ),
                )
            }
            "flow" -> {
                val qualifiedName = section["qualifiedName"] ?: return
                val name = section["name"] ?: qualifiedName.substringAfterLast(".")
                val packageName = section["package"] ?: ""
                val containingClass = section["class"]
                val isProperty = section["isProperty"]?.toBooleanStrictOrNull() ?: false
                val hasAnnotation = section["hasAnnotation"]?.toBooleanStrictOrNull() ?: false

                val params =
                    parameters.map { (paramName, typeInfo) ->
                        // typeInfo can be "String", "List<String>", etc.
                        val (typeName, defaultValue) = if (typeInfo.contains("=")) {
                            val parts = typeInfo.split("=", limit = 2)
                            parts[0] to parts[1]
                        } else {
                            typeInfo to null
                        }
                        ParameterDeclaration(
                            name = paramName,
                            typeName = typeName.removeSuffix("?"),
                            isNullable = typeName.endsWith("?"),
                            defaultValue = defaultValue,
                        )
                    }

                declarations.add(
                    FlowFunctionDeclaration(
                        qualifiedName = qualifiedName,
                        simpleName = name,
                        packageName = packageName,
                        name = name,
                        parameters = params,
                        elementTypeName = section["element"] ?: "Any",
                        hasSwiftFlowAnnotation = hasAnnotation,
                        isProperty = isProperty,
                        containingClassName = containingClass,
                    ),
                )
            }
        }
    }

    private data class SubclassInfo(
        val name: String,
        val properties: List<PropertyDeclaration>,
        val isObject: Boolean,
    )
}
