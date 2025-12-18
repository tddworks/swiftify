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
        // Try to find Apple targets
        try {
            val kotlin = project.extensions.getByName("kotlin")

            // Use reflection to access targets since we don't have KGP dependency
            val targetsMethod = kotlin.javaClass.getMethod("getTargets")
            val targets = targetsMethod.invoke(kotlin)

            if (targets is Iterable<*>) {
                targets.filterNotNull().forEach { target ->
                    val targetName = target.javaClass.getMethod("getName").invoke(target) as? String
                    if (targetName != null && isAppleTarget(targetName)) {
                        configureAppleTarget(project, extension, targetName)
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.debug("Swiftify: Could not configure KMP targets: ${e.message}")
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

        // Register a task to generate Swift for this target
        val generateTaskName = "swiftifyGenerate${targetName.replaceFirstChar { it.uppercase() }}"

        project.tasks.register(generateTaskName, SwiftifyGenerateTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Generate Swift code for $targetName"
            task.outputDirectory.set(
                extension.outputDirectory.map { it.dir(targetName) }
            )
            task.targetName.set(targetName)
        }

        // Register link task for framework targets
        val linkTaskName = "swiftifyLink${targetName.replaceFirstChar { it.uppercase() }}"
        project.tasks.register(linkTaskName, SwiftifyLinkTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Link Swiftify extensions into $targetName framework"
            task.outputDirectory.set(
                extension.outputDirectory.map { it.dir("$targetName/linked") }
            )

            // Configure manifest file from KSP output
            val kspOutputDir = project.layout.buildDirectory.dir("generated/ksp")
            task.manifestFile.set(
                kspOutputDir.map { it.file("${targetName}Main/resources/swiftify-manifest.txt") }
            )

            // Set default framework directory based on convention
            task.frameworkDirectory.set(
                project.layout.buildDirectory.dir("bin/$targetName/debugFramework")
            )
        }
    }
}

/**
 * Swiftify extension for Gradle DSL configuration.
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
