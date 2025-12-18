package io.swiftify.linker

import io.swiftify.common.*
import io.swiftify.generator.SwiftEnumGenerator
import io.swiftify.generator.SwiftAsyncFunctionGenerator
import io.swiftify.generator.SwiftAsyncSequenceGenerator
import java.io.File

/**
 * Processes a Kotlin/Native framework and injects Swiftify extensions.
 *
 * This processor:
 * 1. Reads the Swiftify manifest
 * 2. Generates Swift code based on the manifest
 * 3. Updates the framework with the generated Swift code
 * 4. Optionally updates ApiNotes for better Swift interop
 */
class FrameworkProcessor(
    private val config: FrameworkProcessorConfig
) {
    private val enumGenerator = SwiftEnumGenerator()
    private val asyncFunctionGenerator = SwiftAsyncFunctionGenerator()
    private val asyncSequenceGenerator = SwiftAsyncSequenceGenerator()
    private val linkerPlugin = SwiftifyLinkerPlugin(config.linkerConfig)

    /**
     * Process a framework with Swiftify transformations.
     *
     * @param frameworkDir The .framework directory
     * @param manifestFile The Swiftify manifest file
     * @return Processing result
     */
    fun process(frameworkDir: File, manifestFile: File): ProcessingResult {
        if (!frameworkDir.exists()) {
            return ProcessingResult.Error("Framework not found: ${frameworkDir.absolutePath}")
        }

        if (!manifestFile.exists()) {
            return ProcessingResult.Success(
                frameworkName = frameworkDir.nameWithoutExtension,
                swiftFilesGenerated = 0,
                message = "No manifest file found, skipping Swiftify processing"
            )
        }

        return try {
            val specs = parseManifest(manifestFile)
            if (specs.isEmpty()) {
                return ProcessingResult.Success(
                    frameworkName = frameworkDir.nameWithoutExtension,
                    swiftFilesGenerated = 0,
                    message = "No declarations to transform"
                )
            }

            // Generate Swift code
            val swiftCode = generateSwiftCode(specs)

            // Write Swift files to temp directory
            val tempSwiftDir = createTempSwiftDir(frameworkDir)
            val swiftFile = File(tempSwiftDir, "Swiftify.swift")
            swiftFile.writeText(buildSwiftFile(frameworkDir.nameWithoutExtension, swiftCode))

            // Inject into framework
            val injectionResult = linkerPlugin.inject(frameworkDir, tempSwiftDir)

            when (injectionResult) {
                is InjectionResult.Success -> ProcessingResult.Success(
                    frameworkName = frameworkDir.nameWithoutExtension,
                    swiftFilesGenerated = 1,
                    message = injectionResult.message
                )
                is InjectionResult.Error -> ProcessingResult.Error(injectionResult.message)
            }
        } catch (e: Exception) {
            ProcessingResult.Error("Framework processing failed: ${e.message}")
        }
    }

    private fun parseManifest(file: File): List<SwiftSpec> {
        val specs = mutableListOf<SwiftSpec>()
        var currentSection: MutableMap<String, String>? = null
        var currentType: String? = null
        val subclasses = mutableListOf<Pair<String, Boolean>>()
        val parameters = mutableListOf<Pair<String, String>>()

        file.readLines().forEach { line ->
            when {
                line.startsWith("[sealed:") -> {
                    saveSpec(currentType, currentSection, subclasses, parameters, specs)
                    currentType = "sealed"
                    currentSection = mutableMapOf()
                    subclasses.clear()
                }
                line.startsWith("[suspend:") -> {
                    saveSpec(currentType, currentSection, subclasses, parameters, specs)
                    currentType = "suspend"
                    currentSection = mutableMapOf()
                    parameters.clear()
                }
                line.startsWith("[flow:") -> {
                    saveSpec(currentType, currentSection, subclasses, parameters, specs)
                    currentType = "flow"
                    currentSection = mutableMapOf()
                    parameters.clear()
                }
                line.startsWith("subclass=") -> {
                    val parts = line.substringAfter("subclass=").split(":")
                    subclasses.add(parts[0] to (parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false))
                }
                line.startsWith("param=") -> {
                    val parts = line.substringAfter("param=").split(":")
                    parameters.add(parts[0] to (parts.getOrNull(1) ?: "Any"))
                }
                line.contains("=") && currentSection != null -> {
                    val (key, value) = line.split("=", limit = 2)
                    currentSection!![key] = value
                }
            }
        }

        saveSpec(currentType, currentSection, subclasses, parameters, specs)
        return specs
    }

    private fun saveSpec(
        type: String?,
        section: Map<String, String>?,
        subclasses: List<Pair<String, Boolean>>,
        parameters: List<Pair<String, String>>,
        specs: MutableList<SwiftSpec>
    ) {
        if (type == null || section == null) return

        when (type) {
            "sealed" -> {
                val cases = subclasses.map { (name, isObject) ->
                    if (isObject) {
                        SwiftEnumCase(name.replaceFirstChar { it.lowercase() })
                    } else {
                        SwiftEnumCase(
                            name.replaceFirstChar { it.lowercase() },
                            listOf(SwiftEnumCase.AssociatedValue("value", SwiftType.Named(name)))
                        )
                    }
                }
                specs.add(
                    SwiftSpec.Enum(
                        SwiftEnumSpec(
                            name = section["swiftName"] ?: section["name"] ?: "",
                            cases = cases,
                            isExhaustive = section["exhaustive"]?.toBooleanStrictOrNull() ?: true
                        )
                    )
                )
            }
            "suspend" -> {
                val params = parameters.map { (name, typeName) ->
                    SwiftParameter(name, mapKotlinTypeToSwift(typeName))
                }
                specs.add(
                    SwiftSpec.AsyncFunction(
                        SwiftAsyncFunctionSpec(
                            name = section["name"] ?: "",
                            parameters = params,
                            returnType = mapKotlinTypeToSwift(section["return"] ?: "Unit"),
                            isThrowing = section["throwing"]?.toBooleanStrictOrNull() ?: true
                        )
                    )
                )
            }
            "flow" -> {
                val params = parameters.map { (name, typeName) ->
                    SwiftParameter(name, mapKotlinTypeToSwift(typeName))
                }
                specs.add(
                    SwiftSpec.AsyncSequence(
                        SwiftAsyncSequenceSpec(
                            name = section["name"] ?: "",
                            parameters = params,
                            elementType = mapKotlinTypeToSwift(section["element"] ?: "Any")
                        )
                    )
                )
            }
        }
    }

    private fun mapKotlinTypeToSwift(kotlinType: String): SwiftType {
        return when (kotlinType) {
            "String" -> SwiftType.Named("String")
            "Int" -> SwiftType.Named("Int")
            "Long" -> SwiftType.Named("Int64")
            "Float" -> SwiftType.Named("Float")
            "Double" -> SwiftType.Named("Double")
            "Boolean" -> SwiftType.Named("Bool")
            "Unit" -> SwiftType.Void
            else -> SwiftType.Named(kotlinType)
        }
    }

    private fun generateSwiftCode(specs: List<SwiftSpec>): String {
        return specs.joinToString("\n\n") { spec ->
            when (spec) {
                is SwiftSpec.Enum -> enumGenerator.generate(spec.spec)
                is SwiftSpec.AsyncFunction -> asyncFunctionGenerator.generate(spec.spec)
                is SwiftSpec.AsyncSequence -> asyncSequenceGenerator.generate(spec.spec)
            }
        }
    }

    private fun createTempSwiftDir(frameworkDir: File): File {
        val tempDir = File(frameworkDir.parentFile, ".swiftify-temp")
        tempDir.mkdirs()
        return tempDir
    }

    private fun buildSwiftFile(frameworkName: String, content: String): String = """
        |// Generated by Swiftify
        |// Framework: $frameworkName
        |// Do not edit - this file is auto-generated
        |
        |import Foundation
        |import $frameworkName
        |
        |$content
    """.trimMargin()

    private sealed class SwiftSpec {
        data class Enum(val spec: SwiftEnumSpec) : SwiftSpec()
        data class AsyncFunction(val spec: SwiftAsyncFunctionSpec) : SwiftSpec()
        data class AsyncSequence(val spec: SwiftAsyncSequenceSpec) : SwiftSpec()
    }
}

/**
 * Configuration for framework processing.
 */
data class FrameworkProcessorConfig(
    val linkerConfig: SwiftifyLinkerConfig = SwiftifyLinkerConfig()
)

/**
 * Result of framework processing.
 */
sealed class ProcessingResult {
    data class Success(
        val frameworkName: String,
        val swiftFilesGenerated: Int,
        val message: String
    ) : ProcessingResult()

    data class Error(val message: String) : ProcessingResult()
}
