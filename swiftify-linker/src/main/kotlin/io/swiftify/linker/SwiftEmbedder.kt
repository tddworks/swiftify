package io.swiftify.linker

import java.io.File

/**
 * Orchestrates the Swift embedding process into a Kotlin/Native framework.
 *
 * Single Responsibility: Coordinate the embedding workflow.
 *
 * Flow:
 * 1. Analyze framework (FrameworkAnalyzer)
 * 2. Compile Swift to .o files (SwiftCompiler)
 * 3. Merge .o files into framework binary (BinaryMerger)
 */
class SwiftEmbedder(
    private val analyzer: FrameworkAnalyzer = FrameworkAnalyzer(),
    private val compiler: SwiftCompiler = SwiftCompiler(),
    private val merger: BinaryMerger = BinaryMerger()
) {

    /**
     * Embeds Swift code into a Kotlin/Native framework.
     *
     * @param frameworkDir The .framework directory
     * @param swiftFiles Swift source files to embed
     * @param config Optional embedding configuration
     * @return EmbedResult with success/failure status
     */
    fun embed(
        frameworkDir: File,
        swiftFiles: List<File>,
        config: EmbedConfig = EmbedConfig()
    ): EmbedResult {
        if (swiftFiles.isEmpty()) {
            return EmbedResult.Error("No Swift files provided")
        }

        // Step 1: Analyze framework
        val frameworkInfo = try {
            analyzer.analyze(frameworkDir)
        } catch (e: FrameworkAnalysisException) {
            return EmbedResult.Error("Framework analysis failed: ${e.message}")
        }

        config.logger?.invoke("Analyzed framework: ${frameworkInfo.name} (${frameworkInfo.platform}, ${frameworkInfo.targetTriple})")

        // Step 2: Compile Swift to object files
        val compileConfig = CompileConfig(
            frameworkPath = frameworkDir,
            targetTriple = frameworkInfo.targetTriple,
            sdkPath = config.sdkPath,
            outputDirectory = config.workingDirectory,
            dryRun = config.dryRun,
            logger = config.logger
        )

        val compileResult = compiler.compile(swiftFiles, compileConfig)
        if (compileResult is CompileResult.Error) {
            return EmbedResult.Error("Compilation failed: ${compileResult.message}")
        }

        val objectFiles = (compileResult as CompileResult.Success).objectFiles
        config.logger?.invoke("Compiled ${objectFiles.size} object files")

        // Step 3: Merge object files into framework binary
        val mergeConfig = MergeConfig(
            originalBinary = frameworkInfo.binaryPath,
            targetTriple = frameworkInfo.targetTriple,
            dryRun = config.dryRun,
            logger = config.logger
        )

        val mergeResult = merger.merge(objectFiles, mergeConfig)
        if (mergeResult is MergeResult.Error) {
            return EmbedResult.Error("Merge failed: ${mergeResult.message}")
        }

        val outputBinary = (mergeResult as MergeResult.Success).outputBinary
        config.logger?.invoke("Embedded Swift into ${outputBinary.absolutePath}")

        return EmbedResult.Success(
            frameworkName = frameworkInfo.name,
            binaryPath = outputBinary,
            swiftFilesEmbedded = swiftFiles.size
        )
    }
}

/**
 * Configuration for Swift embedding.
 */
data class EmbedConfig(
    /** Path to the SDK for compilation */
    val sdkPath: String? = null,
    /** Working directory for intermediate files */
    val workingDirectory: File? = null,
    /** Dry run mode - don't execute commands */
    val dryRun: Boolean = false,
    /** Logger for output */
    val logger: ((String) -> Unit)? = null
)

/**
 * Result of Swift embedding.
 */
sealed class EmbedResult {
    /** Successful embedding */
    data class Success(
        val frameworkName: String,
        val binaryPath: File,
        val swiftFilesEmbedded: Int
    ) : EmbedResult()

    /** Embedding failed */
    data class Error(val message: String) : EmbedResult()
}
