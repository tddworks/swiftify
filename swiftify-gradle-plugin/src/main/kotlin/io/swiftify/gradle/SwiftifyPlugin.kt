package io.swiftify.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

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

    override fun apply(project: Project) {
        // Create extension for configuration
        val extension = project.extensions.create(
            "swiftify",
            SwiftifyExtension::class.java,
            project
        )

        // Register tasks
        registerPreviewTask(project, extension)

        // Configure after evaluation to ensure KMP plugin is applied
        project.afterEvaluate {
            if (extension.enabled.get()) {
                configureSwiftifyIntegration(project, extension)
            }
        }
    }

    private fun registerPreviewTask(project: Project, extension: SwiftifyExtension): TaskProvider<SwiftifyPreviewTask> {
        return project.tasks.register("swiftifyPreview", SwiftifyPreviewTask::class.java) { task ->
            task.group = "swiftify"
            task.description = "Preview generated Swift code for Kotlin declarations"
        }
    }

    private fun configureSwiftifyIntegration(project: Project, extension: SwiftifyExtension) {
        // TODO: Configure KSP processor
        // TODO: Configure linker plugin for Apple targets
        project.logger.lifecycle("Swiftify: Configured for project ${project.name}")
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

    init {
        enabled.convention(true)
    }

    // DSL configuration will be delegated to SwiftifySpec
    // This provides the user-facing Gradle DSL

    /**
     * Configure sealed class transformations.
     */
    fun sealedClasses(configure: SealedClassConfig.() -> Unit) {
        SealedClassConfig().apply(configure)
    }

    /**
     * Configure suspend function transformations.
     */
    fun suspendFunctions(configure: SuspendFunctionConfig.() -> Unit) {
        SuspendFunctionConfig().apply(configure)
    }

    /**
     * Configure Flow transformations.
     */
    fun flowTypes(configure: FlowConfig.() -> Unit) {
        FlowConfig().apply(configure)
    }
}

class SealedClassConfig {
    var exhaustive: Boolean = true
    var conformances: List<String> = emptyList()

    fun transformToEnum(exhaustive: Boolean = true) {
        this.exhaustive = exhaustive
    }

    fun conformTo(vararg protocols: String) {
        conformances = protocols.toList()
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
