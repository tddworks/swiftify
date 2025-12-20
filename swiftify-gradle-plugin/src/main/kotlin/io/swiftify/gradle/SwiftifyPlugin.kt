package io.swiftify.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

/**
 * Analysis mode for Swiftify.
 */
enum class AnalysisMode {
    /**
     * Use regex-based source analysis (default).
     * Simple, fast, no additional dependencies.
     */
    REGEX,

    /**
     * Use KSP (Kotlin Symbol Processing) for analysis.
     * More accurate, handles all edge cases, requires KSP plugin.
     */
    KSP,
}

/**
 * Swiftify Gradle Plugin.
 *
 * Enhances Kotlin Multiplatform Swift interfaces with:
 * - Sealed class → Swift enum transformation
 * - Suspend function → async/await transformation
 * - Flow → AsyncStream transformation
 * - Default argument handling
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("io.swiftify") version "1.0.0"
 * }
 *
 * swiftify {
 *     // Choose analysis mode (default: REGEX)
 *     analysisMode(AnalysisMode.REGEX)  // or AnalysisMode.KSP
 *
 *     sealedClasses {
 *         transformToEnum(exhaustive = true)
 *     }
 *     defaultParameters {
 *         generateOverloads(maxOverloads = 5)
 *     }
 *     flowTypes {
 *         transformToAsyncStream()
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
        val extension =
            project.extensions.create(
                "swiftify",
                SwiftifyExtension::class.java,
                project,
            )

        // Set default output directory
        extension.outputDirectory.convention(
            project.layout.buildDirectory.dir("generated/swiftify"),
        )

        // Register tasks
        registerPreviewTask(project, extension)
        registerGenerateTask(project, extension)
        registerProcessManifestTask(project, extension)
        registerEmbedTask(project, extension)

        // Configure after evaluation to ensure KMP plugin is applied
        project.afterEvaluate {
            if (extension.enabled.get()) {
                // Configure KSP if KSP mode is selected
                if (extension.analysisMode.get() == AnalysisMode.KSP) {
                    configureKsp(project, extension)
                }
                configureSwiftifyIntegration(project, extension)
            }
        }
    }

    private fun configureKsp(
        project: Project,
        extension: SwiftifyExtension,
    ) {
        // Check if KSP plugin is applied
        if (!project.plugins.hasPlugin("com.google.devtools.ksp")) {
            project.logger.warn(
                "Swiftify: KSP analysis mode selected but KSP plugin not found. " +
                    "Add 'id(\"com.google.devtools.ksp\")' to your plugins block, or use analysisMode(AnalysisMode.REGEX).",
            )
            return
        }

        project.logger.info("Swiftify: Configuring KSP-based analysis")

        // Add KSP processor dependency
        // For KMP projects, use target-specific configurations like kspJvm
        // For non-KMP projects, use the generic ksp configuration
        val processorDep = "$PROCESSOR_ARTIFACT:${project.version}"
        addKspProcessorDependency(project, processorDep)

        // Configure KSP options
        try {
            val kspExtension = project.extensions.findByName("ksp")
            if (kspExtension != null) {
                val argMethod = kspExtension.javaClass.getMethod("arg", String::class.java, String::class.java)
                argMethod.invoke(
                    kspExtension,
                    "swiftify.outputDir",
                    extension.outputDirectory.get().asFile.absolutePath,
                )
                argMethod.invoke(kspExtension, "swiftify.enabled", extension.enabled.get().toString())
            }
        } catch (e: Exception) {
            project.logger.debug("Swiftify: Could not configure KSP options: ${e.message}")
        }
    }

    /**
     * Add KSP processor dependency to the appropriate configuration(s).
     * For KMP projects, adds to kspJvm (and other JVM-based configurations).
     * For non-KMP projects, adds to the generic ksp configuration.
     */
    private fun addKspProcessorDependency(project: Project, processorDep: String) {
        val configurations = project.configurations

        // Try target-specific KSP configurations for KMP projects
        val kmpKspConfigs = listOf("kspJvm", "kspCommonMainMetadata")
        var addedToAny = false

        for (configName in kmpKspConfigs) {
            try {
                if (configurations.findByName(configName) != null) {
                    project.dependencies.add(configName, processorDep)
                    project.logger.info("Swiftify: Added KSP processor to $configName")
                    addedToAny = true
                }
            } catch (e: Exception) {
                project.logger.debug("Swiftify: Could not add to $configName: ${e.message}")
            }
        }

        // Fallback to generic ksp configuration for non-KMP projects
        if (!addedToAny) {
            try {
                if (configurations.findByName("ksp") != null) {
                    project.dependencies.add("ksp", processorDep)
                    project.logger.info("Swiftify: Added KSP processor to ksp")
                }
            } catch (e: Exception) {
                project.logger.debug("Swiftify: Could not add KSP processor: ${e.message}")
            }
        }
    }

    private fun registerPreviewTask(
        project: Project,
        extension: SwiftifyExtension,
    ): TaskProvider<SwiftifyPreviewTask> = project.tasks.register("swiftifyPreview", SwiftifyPreviewTask::class.java) { task ->
        task.group = "swiftify"
        task.description = "Preview generated Swift code for Kotlin declarations"
    }

    private fun registerGenerateTask(
        project: Project,
        extension: SwiftifyExtension,
    ): TaskProvider<SwiftifyGenerateTask> {
        val taskProvider =
            project.tasks.register("swiftifyGenerate", SwiftifyGenerateTask::class.java) { task ->
                task.group = "swiftify"
                task.description = "Generate Swift code from Kotlin declarations"
                task.outputDirectory.set(extension.outputDirectory)
                task.frameworkName.set(extension.frameworkName)
                task.analysisMode.set(extension.analysisMode)

                // Configure manifest files for KSP mode
                val locator = ManifestLocator(project)
                task.manifestFiles.set(
                    project.provider { locator.locateManifests() },
                )
            }

        // Configure KSP dependencies after evaluation (when user config is processed)
        project.afterEvaluate {
            if (extension.analysisMode.get() == AnalysisMode.KSP) {
                // Find all KSP tasks and add dependencies
                project.tasks.matching { task ->
                    task.name.startsWith("ksp") && task.name.contains("Kotlin")
                }.forEach { kspTask ->
                    taskProvider.configure { task ->
                        task.dependsOn(kspTask)
                        project.logger.info("Swiftify: swiftifyGenerate depends on ${kspTask.name}")
                    }
                }
            }
        }

        return taskProvider
    }

    private fun registerProcessManifestTask(
        project: Project,
        extension: SwiftifyExtension,
    ): TaskProvider<SwiftifyProcessManifestTask> {
        // Deprecated: Use swiftifyGenerate instead
        // Kept for backwards compatibility
        val taskProvider =
            project.tasks.register("swiftifyProcessManifest", SwiftifyProcessManifestTask::class.java) { task ->
                task.group = "swiftify"
                task.description = "[Deprecated] Use swiftifyGenerate instead"
                task.outputDirectory.set(extension.outputDirectory)

                // Auto-detect manifest files from all KSP targets
                val locator = ManifestLocator(project)
                task.manifestFiles.set(
                    project.provider { locator.locateManifests() },
                )

                // Only enable in KSP mode
                task.onlyIf { extension.analysisMode.get() == AnalysisMode.KSP }
            }

        // Depend on KSP task if it exists
        project.tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach { kspTask ->
            taskProvider.configure { it.dependsOn(kspTask) }
        }

        return taskProvider
    }

    private fun registerEmbedTask(
        project: Project,
        extension: SwiftifyExtension,
    ): TaskProvider<SwiftifyEmbedTask> = project.tasks.register("swiftifyEmbed", SwiftifyEmbedTask::class.java) { task ->
        task.group = "swiftify"
        task.description = "Embed Swift extensions into framework binary"
        task.swiftSourceDirectory.set(extension.outputDirectory)

        // Always depend on swiftifyGenerate (works for both REGEX and KSP modes)
        task.dependsOn("swiftifyGenerate")
    }

    private fun configureSwiftifyIntegration(
        project: Project,
        extension: SwiftifyExtension,
    ) {
        // Check for KMP plugin
        val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

        if (hasKmp) {
            configureKmpIntegration(project, extension)
        } else {
            project.logger.warn("Swiftify: Kotlin Multiplatform plugin not found. Some features may not work.")
        }

        val mode = extension.analysisMode.get()
        project.logger.lifecycle("Swiftify: Configured for project ${project.name} (mode: $mode)")
    }

    private fun configureKmpIntegration(
        project: Project,
        extension: SwiftifyExtension,
    ) {
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
                val defaultConvention = project.name.replaceFirstChar { it.uppercase() }
                val currentValue = extension.frameworkName.orNull
                val wasExplicitlySet =
                    extension.frameworkNameExplicitlySet ||
                        (currentValue != null && currentValue != defaultConvention)

                if (frameworkNameDetected != null && !wasExplicitlySet) {
                    extension.frameworkName.set(frameworkNameDetected)
                    project.logger.lifecycle("Swiftify: Auto-detected framework name: $frameworkNameDetected")
                } else if (frameworkNameDetected != null && wasExplicitlySet && currentValue != frameworkNameDetected) {
                    project.logger.warn(
                        "Swiftify: Framework name '$currentValue' was set, but detected '$frameworkNameDetected' from KMP config. " +
                            "Consider removing frameworkName.set() as Swiftify auto-detects it.",
                    )
                }
            }
        } catch (e: Exception) {
            project.logger.debug("Swiftify: Could not configure KMP targets: ${e.message}")
        }
    }

    private fun detectFrameworkName(target: Any): String? {
        return try {
            val binariesMethod = target.javaClass.getMethod("getBinaries")
            val binaries = binariesMethod.invoke(target) ?: return null

            val frameworks =
                if (binaries is Iterable<*>) {
                    binaries.filterNotNull().filter { binary ->
                        binary.javaClass.simpleName.contains("Framework")
                    }
                } else {
                    emptyList()
                }

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
        val appleTargets =
            listOf(
                "ios", "watchos", "tvos", "macos",
                "iosArm64", "iosX64", "iosSimulatorArm64",
                "watchosArm32", "watchosArm64", "watchosX64", "watchosSimulatorArm64",
                "tvosArm64", "tvosX64", "tvosSimulatorArm64",
                "macosArm64", "macosX64",
            )
        return appleTargets.any { targetName.contains(it, ignoreCase = true) }
    }

    private fun configureAppleTarget(
        project: Project,
        extension: SwiftifyExtension,
        targetName: String,
    ) {
        project.logger.info("Swiftify: Configuring Apple target: $targetName")
        registerTargetEmbedTasks(project, extension, targetName)
    }

    private fun registerTargetEmbedTasks(
        project: Project,
        extension: SwiftifyExtension,
        targetName: String,
    ) {
        val capitalizedTarget = targetName.replaceFirstChar { it.uppercase() }

        listOf("Debug", "Release").forEach { buildType ->
            val linkTaskName = "link${buildType}Framework$capitalizedTarget"
            val embedTaskName = "swiftifyEmbed${buildType}$capitalizedTarget"

            val embedTaskProvider =
                project.tasks.register(embedTaskName, SwiftifyEmbedTask::class.java) { embedTask ->
                    embedTask.group = "swiftify"
                    embedTask.description = "Embed Swift extensions into $buildType framework for $targetName"
                    embedTask.swiftSourceDirectory.set(extension.outputDirectory)

                    val frameworkDir =
                        project.layout.buildDirectory.dir(
                            "bin/$targetName/${buildType.lowercase()}Framework/${extension.frameworkName.get()}.framework",
                        )
                    embedTask.frameworkDirectory.set(frameworkDir)

                    // Always depend on swiftifyGenerate (works for both REGEX and KSP modes)
                    embedTask.dependsOn("swiftifyGenerate")
                }

            project.tasks.matching { it.name == linkTaskName }.configureEach { linkTask ->
                linkTask.finalizedBy(embedTaskName)
                project.logger.info("Swiftify: Hooked $embedTaskName into $linkTaskName")
            }
        }
    }
}

/**
 * Swiftify extension for Gradle DSL configuration.
 */
abstract class SwiftifyExtension(
    private val project: Project,
) {
    /** Whether Swiftify is enabled. Default: true. */
    abstract val enabled: Property<Boolean>

    /** Output directory for generated Swift files. */
    abstract val outputDirectory: DirectoryProperty

    /** Framework name for imports. Auto-detected from KMP config. */
    abstract val frameworkName: Property<String>

    /** Analysis mode: REGEX (default) or KSP. */
    abstract val analysisMode: Property<AnalysisMode>

    internal var frameworkNameExplicitlySet: Boolean = false
        private set

    val sealedClassConfig = SealedClassConfig()
    val defaultParameterConfig = DefaultParameterConfig()
    val flowConfig = FlowConfig()

    init {
        enabled.convention(true)
        frameworkName.convention(project.name.replaceFirstChar { it.uppercase() })
        analysisMode.convention(AnalysisMode.REGEX)
    }

    /** Set the framework name explicitly. */
    fun frameworkName(name: String) {
        frameworkName.set(name)
        frameworkNameExplicitlySet = true
    }

    /**
     * Set the analysis mode.
     *
     * @param mode REGEX (default, simple) or KSP (accurate, requires KSP plugin)
     */
    fun analysisMode(mode: AnalysisMode) {
        analysisMode.set(mode)
    }

    fun sealedClasses(configure: SealedClassConfig.() -> Unit) {
        sealedClassConfig.apply(configure)
    }

    fun defaultParameters(configure: DefaultParameterConfig.() -> Unit) {
        defaultParameterConfig.apply(configure)
    }

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

class DefaultParameterConfig {
    var maxOverloads: Int = 5

    fun generateOverloads(maxOverloads: Int = 5) {
        this.maxOverloads = maxOverloads
    }
}

class FlowConfig {
    var useAsyncStream: Boolean = true

    fun transformToAsyncStream() {
        useAsyncStream = true
    }
}
