package io.swiftify.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Swiftify Gradle Plugin.
 *
 * Enhances Kotlin Multiplatform Swift interfaces with:
 * - Sealed class → Swift enum transformation
 * - Suspend function → async/await transformation
 * - Flow → AsyncSequence transformation
 * - Default argument handling
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("io.swiftify") version "1.0.0"
 * }
 *
 * swiftify {
 *     // Optional configuration - sensible defaults work out of the box
 *     sealedClasses {
 *         transformToEnum(exhaustive = true)
 *     }
 * }
 * ```
 */
class SwiftifyPlugin : Plugin<Project> {

    companion object {
        const val SWIFTIFY_GROUP = "swiftify"
        const val PROCESSOR_ARTIFACT = "io.swiftify:swiftify-analyzer"
    }

    override fun apply(project: Project) {
        // Create extension for configuration
        val extension = project.extensions.create(
            "swiftify",
            SwiftifyExtension::class.java,
            project
        )

        // Set default output directory
        extension.outputDirectory.convention(
            project.layout.buildDirectory.dir("generated/swiftify")
        )

        // Register tasks
        registerPreviewTask(project, extension)
        registerGenerateTask(project, extension)
        registerProcessManifestTask(project, extension)
        registerEmbedTask(project, extension)

        // Configure KSP if available
        configureKsp(project, extension)

        // Configure after evaluation to ensure KMP plugin is applied
        project.afterEvaluate {
            if (extension.enabled.get()) {
                configureSwiftifyIntegration(project, extension)
            }
        }
    }

    private fun configureKsp(project: Project, extension: SwiftifyExtension) {
        // Wait for KSP plugin to be applied
        project.plugins.withId("com.google.devtools.ksp") {
            project.logger.info("Swiftify: KSP plugin detected, configuring processor")

            // Add KSP processor dependency
            project.dependencies.add("ksp", project.project(":swiftify-analyzer"))

            // Configure KSP options
            project.afterEvaluate {
                try {
                    val kspExtension = project.extensions.findByName("ksp")
                    if (kspExtension != null) {
                        val argMethod = kspExtension.javaClass.getMethod("arg", String::class.java, String::class.java)
                        argMethod.invoke(kspExtension, "swiftify.outputDir", extension.outputDirectory.get().asFile.absolutePath)
                        argMethod.invoke(kspExtension, "swiftify.enabled", extension.enabled.get().toString())
                    }
                } catch (e: Exception) {
                    project.logger.debug("Swiftify: Could not configure KSP options: ${e.message}")
                }
            }
        }
    }

    private fun registerPreviewTask(project: Project, extension: SwiftifyExtension): TaskProvider<SwiftifyPreviewTask> {
        return project.tasks.register("swiftifyPreview", SwiftifyPreviewTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Preview generated Swift code for Kotlin declarations"
        }
    }

    private fun registerGenerateTask(project: Project, extension: SwiftifyExtension): TaskProvider<SwiftifyGenerateTask> {
        return project.tasks.register("swiftifyGenerate", SwiftifyGenerateTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Generate Swift code from Kotlin declarations"
            task.outputDirectory.set(extension.outputDirectory)
            task.frameworkName.set(extension.frameworkName)
        }
    }

    private fun registerProcessManifestTask(project: Project, extension: SwiftifyExtension): TaskProvider<SwiftifyProcessManifestTask> {
        val taskProvider = project.tasks.register("swiftifyProcessManifest", SwiftifyProcessManifestTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Process KSP manifest and generate Swift code"
            task.outputDirectory.set(extension.outputDirectory)

            // Configure manifest file location from KSP output using lazy provider
            val kspOutputDir = project.layout.buildDirectory.dir("generated/ksp")
            task.manifestFile.set(
                kspOutputDir.map { it.file("main/resources/swiftify-manifest.txt") }
            )
        }

        // Depend on KSP task if it exists (outside task configuration block)
        project.tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach { kspTask ->
            taskProvider.configure { it.dependsOn(kspTask) }
        }

        return taskProvider
    }

    private fun registerEmbedTask(project: Project, extension: SwiftifyExtension): TaskProvider<SwiftifyEmbedTask> {
        return project.tasks.register("swiftifyEmbed", SwiftifyEmbedTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Embed Swift extensions into framework binary"
            task.swiftSourceDirectory.set(extension.outputDirectory)

            // Depend on swiftifyGenerate to ensure Swift files are generated first
            task.dependsOn("swiftifyGenerate")
        }
    }

    private fun configureSwiftifyIntegration(project: Project, extension: SwiftifyExtension) {
        // Check for KMP plugin
        val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

        if (hasKmp) {
            configureKmpIntegration(project, extension)
        } else {
            project.logger.warn("Swiftify: Kotlin Multiplatform plugin not found. Some features may not work.")
        }

        project.logger.lifecycle("Swiftify: Configured for project ${project.name}")
    }

    private fun configureKmpIntegration(project: Project, extension: SwiftifyExtension) {
        // Try to find Apple targets and auto-detect framework name
        try {
            val kotlin = project.extensions.getByName("kotlin")

            // Use reflection to access targets since we don't have KGP dependency
            val targetsMethod = kotlin.javaClass.getMethod("getTargets")
            val targets = targetsMethod.invoke(kotlin)

            if (targets is Iterable<*>) {
                var frameworkNameDetected: String? = null

                targets.filterNotNull().forEach { target ->
                    val targetName = target.javaClass.getMethod("getName").invoke(target) as? String
                    if (targetName != null && isAppleTarget(targetName)) {
                        configureAppleTarget(project, extension, targetName)

                        // Auto-detect framework name from first Apple target's framework binary
                        if (frameworkNameDetected == null) {
                            frameworkNameDetected = detectFrameworkName(target)
                        }
                    }
                }

                // Set auto-detected framework name if user didn't explicitly set one
                // Check both the explicit flag and whether the value differs from convention
                val defaultConvention = project.name.replaceFirstChar { it.uppercase() }
                val currentValue = extension.frameworkName.orNull
                val wasExplicitlySet = extension.frameworkNameExplicitlySet ||
                    (currentValue != null && currentValue != defaultConvention)

                if (frameworkNameDetected != null && !wasExplicitlySet) {
                    extension.frameworkName.set(frameworkNameDetected)
                    project.logger.lifecycle("Swiftify: Auto-detected framework name: $frameworkNameDetected")
                } else if (frameworkNameDetected != null && wasExplicitlySet && currentValue != frameworkNameDetected) {
                    project.logger.warn("Swiftify: Framework name '$currentValue' was set, but detected '$frameworkNameDetected' from KMP config. " +
                        "Consider removing frameworkName.set() as Swiftify auto-detects it.")
                }
            }
        } catch (e: Exception) {
            project.logger.debug("Swiftify: Could not configure KMP targets: ${e.message}")
        }
    }

    /**
     * Auto-detect framework name from a KMP Apple target's binary configuration.
     * Looks for: target.binaries.framework.baseName
     */
    private fun detectFrameworkName(target: Any): String? {
        return try {
            // Get binaries container
            val binariesMethod = target.javaClass.getMethod("getBinaries")
            val binaries = binariesMethod.invoke(target) ?: return null

            // Try to find a framework binary
            val frameworks = if (binaries is Iterable<*>) {
                binaries.filterNotNull().filter { binary ->
                    binary.javaClass.simpleName.contains("Framework")
                }
            } else {
                emptyList()
            }

            // Get baseName from first framework
            frameworks.firstOrNull()?.let { framework ->
                try {
                    val baseNameMethod = framework.javaClass.getMethod("getBaseName")
                    baseNameMethod.invoke(framework) as? String
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isAppleTarget(targetName: String): Boolean {
        val appleTargets = listOf(
            "ios", "watchos", "tvos", "macos",
            "iosArm64", "iosX64", "iosSimulatorArm64",
            "watchosArm32", "watchosArm64", "watchosX64", "watchosSimulatorArm64",
            "tvosArm64", "tvosX64", "tvosSimulatorArm64",
            "macosArm64", "macosX64"
        )
        return appleTargets.any { targetName.contains(it, ignoreCase = true) }
    }

    private fun configureAppleTarget(project: Project, extension: SwiftifyExtension, targetName: String) {
        project.logger.info("Swiftify: Configuring Apple target: $targetName")

        // Hook into KMP framework link tasks to auto-generate Swift code
        // This makes ./gradlew linkDebugFrameworkMacosArm64 automatically run swiftifyGenerate
        hookIntoFrameworkLinkTasks(project, targetName)
    }

    /**
     * Hook swiftifyEmbed into framework link tasks.
     * Swift code is auto-generated and embedded when building frameworks.
     *
     * When you run: ./gradlew linkDebugFrameworkMacosArm64
     * Swiftify will automatically:
     * 1. Generate Swift code (swiftifyGenerate)
     * 2. Embed it into the framework binary (swiftifyEmbed)
     */
    private fun hookIntoFrameworkLinkTasks(
        project: Project,
        targetName: String
    ) {
        // Match tasks like: linkDebugFrameworkMacosArm64, linkReleaseFrameworkIosArm64
        val linkTaskPatterns = listOf(
            "linkDebugFramework${targetName.replaceFirstChar { it.uppercase() }}",
            "linkReleaseFramework${targetName.replaceFirstChar { it.uppercase() }}",
            "link.*Framework${targetName.replaceFirstChar { it.uppercase() }}"
        )

        project.tasks.configureEach { task ->
            val taskName = task.name
            val isFrameworkLinkTask = linkTaskPatterns.any { pattern ->
                if (pattern.contains(".*")) {
                    taskName.matches(Regex(pattern))
                } else {
                    taskName == pattern
                }
            }

            if (isFrameworkLinkTask) {
                // Configure swiftifyEmbed with the framework directory from the link task
                project.tasks.named("swiftifyEmbed", SwiftifyEmbedTask::class.java).configure { embedTask ->
                    // Set framework directory from link task output
                    try {
                        val outputDir = task.javaClass.getMethod("getOutputDirectory").invoke(task)
                        if (outputDir != null) {
                            val dirProperty = outputDir.javaClass.getMethod("get").invoke(outputDir)
                            val asFile = dirProperty.javaClass.getMethod("getAsFile").invoke(dirProperty) as? java.io.File
                            if (asFile != null) {
                                embedTask.frameworkDirectory.set(asFile)
                            }
                        }
                    } catch (e: Exception) {
                        project.logger.debug("Swiftify: Could not auto-configure framework directory: ${e.message}")
                    }
                }

                // Run swiftifyEmbed after the framework is linked
                // swiftifyEmbed already depends on swiftifyGenerate
                task.finalizedBy("swiftifyEmbed")
                project.logger.info("Swiftify: Hooked into $taskName - Swift will be auto-generated and embedded")
            }
        }
    }
}

/**
 * Swiftify extension for Gradle DSL configuration.
 *
 * The framework name is auto-detected from your Kotlin Multiplatform configuration:
 * ```kotlin
 * kotlin {
 *     iosArm64().binaries.framework {
 *         baseName = "MyKit"  // <- This is auto-detected
 *     }
 * }
 * ```
 *
 * You only need to configure transformation rules:
 * ```kotlin
 * swiftify {
 *     sealedClasses { transformToEnum(exhaustive = true) }
 *     suspendFunctions { transformToAsync(throwing = true) }
 *     flowTypes { transformToAsyncSequence() }
 * }
 * ```
 */
abstract class SwiftifyExtension(private val project: Project) {

    /**
     * Whether Swiftify is enabled. Default: true.
     */
    abstract val enabled: Property<Boolean>

    /**
     * Output directory for generated Swift files.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Framework name for imports.
     *
     * This is auto-detected from your KMP framework configuration.
     * Only set this manually if you have a non-standard setup.
     */
    abstract val frameworkName: Property<String>

    /**
     * Tracks if frameworkName was explicitly set by user.
     * If false, we'll auto-detect from KMP configuration.
     */
    internal var frameworkNameExplicitlySet: Boolean = false
        private set

    /**
     * Configuration for sealed class transformations.
     */
    val sealedClassConfig = SealedClassConfig()

    /**
     * Configuration for suspend function transformations.
     */
    val suspendFunctionConfig = SuspendFunctionConfig()

    /**
     * Configuration for Flow transformations.
     */
    val flowConfig = FlowConfig()

    init {
        enabled.convention(true)
        // Default convention - will be overridden by auto-detection if available
        frameworkName.convention(project.name.replaceFirstChar { it.uppercase() })
    }

    /**
     * Explicitly set the framework name.
     * Note: This is usually not needed as Swiftify auto-detects it from KMP config.
     */
    fun frameworkName(name: String) {
        frameworkName.set(name)
        frameworkNameExplicitlySet = true
    }

    /**
     * Configure sealed class transformations.
     */
    fun sealedClasses(configure: SealedClassConfig.() -> Unit) {
        sealedClassConfig.apply(configure)
    }

    /**
     * Configure suspend function transformations.
     */
    fun suspendFunctions(configure: SuspendFunctionConfig.() -> Unit) {
        suspendFunctionConfig.apply(configure)
    }

    /**
     * Configure Flow transformations.
     */
    fun flowTypes(configure: FlowConfig.() -> Unit) {
        flowConfig.apply(configure)
    }
}

class SealedClassConfig {
    var exhaustive: Boolean = true
    var conformances: MutableList<String> = mutableListOf()

    fun transformToEnum(exhaustive: Boolean = true) {
        this.exhaustive = exhaustive
    }

    fun conformTo(vararg protocols: String) {
        conformances.addAll(protocols)
    }
}

class SuspendFunctionConfig {
    var throwing: Boolean = true

    fun transformToAsync(throwing: Boolean = true) {
        this.throwing = throwing
    }
}

class FlowConfig {
    var useAsyncSequence: Boolean = true

    fun transformToAsyncSequence() {
        useAsyncSequence = true
    }
}
