package io.swiftify.generator

import io.swiftify.analyzer.*
import io.swiftify.swift.*

/**
 * Generates .apinotes files for Xcode integration.
 *
 * ApiNotes allow customizing how Kotlin/Native frameworks are imported into Swift:
 * - Rename types and functions
 * - Mark nullability
 * - Add availability annotations
 * - Customize enum importing
 *
 * @see https://clang.llvm.org/docs/APINotes.html
 */
class ApiNotesGenerator {

    /**
     * Generate ApiNotes YAML content for the given declarations.
     *
     * @param frameworkName The name of the framework
     * @param declarations List of analyzed Kotlin declarations
     * @return YAML content for the .apinotes file
     */
    fun generate(frameworkName: String, declarations: List<KotlinDeclaration>): String {
        return buildString {
            appendLine("---")
            appendLine("Name: $frameworkName")
            appendLine("Classes:")

            // Group declarations by their containing class
            val classDeclarations = mutableMapOf<String, MutableList<KotlinDeclaration>>()

            declarations.forEach { decl ->
                when (decl) {
                    is SealedClassDeclaration -> {
                        // Sealed classes get their own entry
                        appendLine(generateSealedClassNotes(decl))
                    }
                    is SuspendFunctionDeclaration -> {
                        // Group by package for now (could be by class if we had that info)
                        val key = decl.packageName.ifEmpty { "Global" }
                        classDeclarations.getOrPut(key) { mutableListOf() }.add(decl)
                    }
                    is FlowFunctionDeclaration -> {
                        val key = decl.packageName.ifEmpty { "Global" }
                        classDeclarations.getOrPut(key) { mutableListOf() }.add(decl)
                    }
                }
            }

            // Generate method notes grouped by class/module
            classDeclarations.forEach { (className, decls) ->
                appendLine(generateClassNotes(className, decls))
            }
        }
    }

    private fun generateSealedClassNotes(decl: SealedClassDeclaration): String = buildString {
        val swiftName = decl.swiftEnumName ?: decl.simpleName

        appendLine("  - Name: ${decl.simpleName}")
        appendLine("    SwiftName: $swiftName")

        // Mark as swift_bridge to indicate it has a Swift enum equivalent
        appendLine("    SwiftBridge: $swiftName")

        // Add subclass mappings
        if (decl.subclasses.isNotEmpty()) {
            appendLine("    # Subclass mappings")
            decl.subclasses.forEach { subclass ->
                val caseName = subclass.simpleName.replaceFirstChar { it.lowercase() }
                appendLine("    # ${subclass.simpleName} -> .$caseName")
            }
        }
    }

    private fun generateClassNotes(className: String, declarations: List<KotlinDeclaration>): String = buildString {
        appendLine("  - Name: $className")
        appendLine("    Methods:")

        declarations.forEach { decl ->
            when (decl) {
                is SuspendFunctionDeclaration -> {
                    appendLine(generateMethodNotes(decl))
                }
                is FlowFunctionDeclaration -> {
                    appendLine(generateFlowNotes(decl))
                }
                else -> {}
            }
        }
    }

    private fun generateMethodNotes(decl: SuspendFunctionDeclaration): String = buildString {
        // Generate selector for Objective-C method
        val selector = generateObjCSelector(decl.name, decl.parameters)

        append("      - Selector: \"$selector\"")
        appendLine()
        append("        SwiftName: \"${decl.name}(")
        append(decl.parameters.joinToString(",") { "${it.name}:" })
        appendLine(")\"")

        // Mark as async
        appendLine("        # Async function - use Swift concurrency")

        // Mark availability if needed
        if (decl.isThrowing) {
            appendLine("        # Throws errors")
        }
    }

    private fun generateFlowNotes(decl: FlowFunctionDeclaration): String = buildString {
        val selector = if (decl.isProperty) {
            decl.name
        } else {
            generateObjCSelector(decl.name, decl.parameters)
        }

        append("      - Selector: \"$selector\"")
        appendLine()
        append("        SwiftName: \"${decl.name}")
        if (!decl.isProperty && decl.parameters.isNotEmpty()) {
            append("(")
            append(decl.parameters.joinToString(",") { "${it.name}:" })
            append(")")
        }
        appendLine("\"")

        // Mark as returning AsyncSequence
        appendLine("        # Returns AsyncSequence<${decl.elementTypeName}>")
    }

    private fun generateObjCSelector(name: String, parameters: List<ParameterDeclaration>): String {
        return if (parameters.isEmpty()) {
            name
        } else {
            buildString {
                append(name)
                parameters.forEachIndexed { index, param ->
                    if (index == 0) {
                        append("With${param.name.replaceFirstChar { it.uppercase() }}:")
                    } else {
                        append("${param.name}:")
                    }
                }
            }
        }
    }

    /**
     * Generate ApiNotes file name for a framework.
     */
    fun getFileName(frameworkName: String): String = "$frameworkName.apinotes"
}
