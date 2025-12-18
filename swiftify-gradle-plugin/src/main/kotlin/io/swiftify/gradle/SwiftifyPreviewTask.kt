package io.swiftify.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task to preview generated Swift code for Kotlin declarations.
 *
 * Usage:
 * ```bash
 * ./gradlew swiftifyPreview
 * ./gradlew swiftifyPreview --class=com.example.NetworkResult
 * ```
 */
abstract class SwiftifyPreviewTask : DefaultTask() {

    /**
     * Specific class to preview. If not set, previews all transformable declarations.
     */
    @get:Input
    @get:Optional
    abstract val targetClass: Property<String>

    init {
        group = "swiftify"
        description = "Preview generated Swift code for Kotlin declarations"
    }

    @TaskAction
    fun preview() {
        val target = if (targetClass.isPresent) targetClass.get() else null

        logger.lifecycle(buildPreviewHeader())

        if (target != null) {
            logger.lifecycle("Previewing: $target")
            // TODO: Implement actual preview logic
            logger.lifecycle(buildMockPreview(target))
        } else {
            logger.lifecycle("Previewing all transformable declarations...")
            logger.lifecycle("(Use --class=com.example.YourClass to preview a specific class)")
        }
    }

    private fun buildPreviewHeader(): String = """
        |
        |╔════════════════════════════════════════════════════════════════╗
        |║                    Swiftify Preview                            ║
        |╚════════════════════════════════════════════════════════════════╝
        |
    """.trimMargin()

    private fun buildMockPreview(className: String): String {
        val simpleName = className.substringAfterLast(".")
        return """
            |
            |┌────────────────────────────────────────────────────────────────┐
            |│ Kotlin Source:                                                 │
            |├────────────────────────────────────────────────────────────────┤
            |│ sealed class $simpleName {                                     │
            |│     data class Success(val data: String) : $simpleName()       │
            |│     data class Failure(val error: Throwable) : $simpleName()   │
            |│ }                                                              │
            |└────────────────────────────────────────────────────────────────┘
            |                              ↓
            |┌────────────────────────────────────────────────────────────────┐
            |│ Generated Swift:                                               │
            |├────────────────────────────────────────────────────────────────┤
            |│ @frozen                                                        │
            |│ public enum $simpleName: Hashable {                            │
            |│     case success(data: String)                                 │
            |│     case failure(error: Error)                                 │
            |│ }                                                              │
            |└────────────────────────────────────────────────────────────────┘
            |
        """.trimMargin()
    }
}
