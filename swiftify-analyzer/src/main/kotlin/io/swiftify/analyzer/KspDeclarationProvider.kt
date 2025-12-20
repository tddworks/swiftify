package io.swiftify.analyzer

import java.io.File

/**
 * Declaration provider that parses KSP-generated manifest files.
 *
 * This provider is suitable for:
 * - Production builds with accurate type resolution
 * - Projects using KSP for code analysis
 * - Multi-target KMP projects (handles manifest merging)
 *
 * Usage with manifest content:
 * ```kotlin
 * val manifestContent = File("swiftify-manifest.txt").readText()
 * val provider = KspDeclarationProvider(manifestContent)
 * val declarations = provider.getDeclarations()
 * ```
 *
 * Usage with manifest files (handles merging):
 * ```kotlin
 * val manifestFiles = listOf(File("build/generated/ksp/jvm/swiftify-manifest.txt"))
 * val provider = KspDeclarationProvider.fromFiles(manifestFiles)
 * val declarations = provider.getDeclarations()
 * ```
 *
 * @param manifestContent The merged manifest content to parse
 */
class KspDeclarationProvider(
    private val manifestContent: String,
) : DeclarationProvider {

    private val parser = ManifestDeclarationParser()

    override fun getDeclarations(): List<KotlinDeclaration> = parser.parse(manifestContent)

    override fun hasValidInput(): Boolean = manifestContent.isNotBlank() &&
        (
            manifestContent.contains("[sealed:") ||
                manifestContent.contains("[suspend:") ||
                manifestContent.contains("[flow:")
            )

    override fun getSourceDescription(): String {
        val lines = manifestContent.lines()
        val sealedCount = lines.count { it.startsWith("[sealed:") }
        val suspendCount = lines.count { it.startsWith("[suspend:") }
        val flowCount = lines.count { it.startsWith("[flow:") }
        return "KSP manifest ($sealedCount sealed, $suspendCount suspend, $flowCount flow)"
    }

    companion object {
        /**
         * Creates a provider from multiple manifest files, merging them.
         *
         * In multi-target KMP projects, KSP generates separate manifests for each target.
         * This method merges them while deduplicating declarations.
         *
         * @param manifestFiles List of manifest files to merge and parse
         * @return A provider with the merged manifest content
         */
        fun fromFiles(manifestFiles: List<File>): KspDeclarationProvider {
            val validFiles = manifestFiles.filter { it.exists() && it.canRead() }
            if (validFiles.isEmpty()) {
                return KspDeclarationProvider("")
            }

            if (validFiles.size == 1) {
                return KspDeclarationProvider(validFiles.first().readText())
            }

            // Merge multiple manifests
            val mergedContent = mergeManifests(validFiles)
            return KspDeclarationProvider(mergedContent)
        }

        /**
         * Merges multiple manifest files into a single manifest content.
         * Deduplicates by qualified name (first occurrence wins).
         */
        private fun mergeManifests(manifestFiles: List<File>): String {
            val allSections = manifestFiles.flatMap { parseManifestSections(it) }
            val uniqueSections = deduplicateSections(allSections)

            return buildString {
                appendLine("# Swiftify Declaration Manifest")
                appendLine("# Merged from ${manifestFiles.size} manifest(s)")
                appendLine()

                uniqueSections.forEach { section ->
                    append(section.content)
                    if (!section.content.endsWith("\n\n")) {
                        appendLine()
                    }
                }
            }
        }

        private fun parseManifestSections(file: File): List<ManifestSection> {
            val sections = mutableListOf<ManifestSection>()
            val lines = file.readLines()
            var currentSection: StringBuilder? = null
            var currentType: String? = null
            var currentQualifiedName: String? = null

            for (line in lines) {
                when {
                    line.startsWith("[sealed:") -> {
                        saveSection(currentSection, currentType, currentQualifiedName, sections)
                        currentType = "sealed"
                        currentQualifiedName = line.substringAfter("[sealed:").substringBefore("]")
                        currentSection = StringBuilder().append(line).append("\n")
                    }
                    line.startsWith("[suspend:") -> {
                        saveSection(currentSection, currentType, currentQualifiedName, sections)
                        currentType = "suspend"
                        currentQualifiedName = line.substringAfter("[suspend:").substringBefore("]")
                        currentSection = StringBuilder().append(line).append("\n")
                    }
                    line.startsWith("[flow:") -> {
                        saveSection(currentSection, currentType, currentQualifiedName, sections)
                        currentType = "flow"
                        currentQualifiedName = line.substringAfter("[flow:").substringBefore("]")
                        currentSection = StringBuilder().append(line).append("\n")
                    }
                    line.isNotBlank() && !line.startsWith("#") && currentSection != null -> {
                        currentSection.append(line).append("\n")
                    }
                }
            }

            saveSection(currentSection, currentType, currentQualifiedName, sections)
            return sections
        }

        private fun saveSection(
            section: StringBuilder?,
            type: String?,
            qualifiedName: String?,
            sections: MutableList<ManifestSection>,
        ) {
            if (section != null && type != null && qualifiedName != null) {
                sections.add(ManifestSection(type, qualifiedName, section.toString()))
            }
        }

        private fun deduplicateSections(sections: List<ManifestSection>): List<ManifestSection> {
            val seen = mutableSetOf<String>()
            return sections.filter { section ->
                val key = "${section.type}:${section.qualifiedName}"
                seen.add(key)
            }
        }

        private data class ManifestSection(
            val type: String,
            val qualifiedName: String,
            val content: String,
        )
    }
}
