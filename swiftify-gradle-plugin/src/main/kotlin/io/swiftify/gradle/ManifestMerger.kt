package io.swiftify.gradle

import java.io.File

/**
 * Merges multiple KSP-generated manifest files into a single consolidated manifest.
 *
 * In multi-target KMP projects, KSP generates separate manifests for each target.
 * This class merges them while deduplicating declarations.
 */
class ManifestMerger {
    /**
     * Merges multiple manifest files into one.
     * Deduplicates by qualified name (first occurrence wins).
     */
    fun merge(manifestFiles: List<File>): String {
        if (manifestFiles.isEmpty()) {
            return ""
        }

        if (manifestFiles.size == 1) {
            return manifestFiles.first().readText()
        }

        // Parse all manifests into sections
        val allSections = manifestFiles.flatMap { parseManifest(it) }

        // Deduplicate
        val uniqueSections = deduplicate(allSections)

        // Rebuild merged manifest
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

    /**
     * Parses a manifest file into declaration sections.
     */
    private fun parseManifest(file: File): List<ManifestSection> {
        val sections = mutableListOf<ManifestSection>()
        val lines = file.readLines()
        var currentSection: StringBuilder? = null
        var currentType: String? = null
        var currentQualifiedName: String? = null

        for (line in lines) {
            when {
                line.startsWith("[sealed:") -> {
                    saveCurrentSection(currentSection, currentType, currentQualifiedName, sections)
                    currentType = "sealed"
                    currentQualifiedName = line.substringAfter("[sealed:").substringBefore("]")
                    currentSection = StringBuilder().append(line).append("\n")
                }
                line.startsWith("[suspend:") -> {
                    saveCurrentSection(currentSection, currentType, currentQualifiedName, sections)
                    currentType = "suspend"
                    currentQualifiedName = line.substringAfter("[suspend:").substringBefore("]")
                    currentSection = StringBuilder().append(line).append("\n")
                }
                line.startsWith("[flow:") -> {
                    saveCurrentSection(currentSection, currentType, currentQualifiedName, sections)
                    currentType = "flow"
                    currentQualifiedName = line.substringAfter("[flow:").substringBefore("]")
                    currentSection = StringBuilder().append(line).append("\n")
                }
                line.isNotBlank() && !line.startsWith("#") && currentSection != null -> {
                    currentSection.append(line).append("\n")
                }
            }
        }

        saveCurrentSection(currentSection, currentType, currentQualifiedName, sections)
        return sections
    }

    private fun saveCurrentSection(
        section: StringBuilder?,
        type: String?,
        qualifiedName: String?,
        sections: MutableList<ManifestSection>,
    ) {
        if (section != null && type != null && qualifiedName != null) {
            sections.add(ManifestSection(type, qualifiedName, section.toString()))
        }
    }

    /**
     * Deduplicates sections by qualified name.
     * First occurrence wins (preserves order from first manifest).
     */
    private fun deduplicate(sections: List<ManifestSection>): List<ManifestSection> {
        val seen = mutableSetOf<String>()
        return sections.filter { section ->
            val key = "${section.type}:${section.qualifiedName}"
            seen.add(key)
        }
    }

    /**
     * Represents a single declaration section in a manifest.
     */
    private data class ManifestSection(
        val type: String,
        val qualifiedName: String,
        val content: String,
    )
}
