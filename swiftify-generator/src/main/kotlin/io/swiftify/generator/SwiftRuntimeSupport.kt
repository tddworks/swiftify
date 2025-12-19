package io.swiftify.generator

/**
 * Generates the Swift runtime support code needed for Swiftify bridging.
 *
 * This includes helper classes like SwiftifyFlowCollector for Flow→AsyncStream bridging.
 */
object SwiftRuntimeSupport {
    /**
     * Generate the complete runtime support Swift code.
     *
     * @param frameworkName The name of the Kotlin framework to import
     */
    fun generate(frameworkName: String = "SharedKit"): String =
        """
        |// MARK: - Swiftify Runtime Support
        |// Helper classes for Kotlin/Swift bridging
        |
        |import Foundation
        |import $frameworkName
        |
        |/// Collector for bridging Kotlin Flow to Swift AsyncStream
        |/// Conforms to Kotlin's FlowCollector protocol
        |public class SwiftifyFlowCollector<T>: NSObject, Kotlinx_coroutines_coreFlowCollector {
        |    private let onEmit: (T) -> Void
        |    private let onComplete: () -> Void
        |    private let onError: (Error) -> Void
        |
        |    public init(
        |        onEmit: @escaping (T) -> Void,
        |        onComplete: @escaping () -> Void,
        |        onError: @escaping (Error) -> Void
        |    ) {
        |        self.onEmit = onEmit
        |        self.onComplete = onComplete
        |        self.onError = onError
        |    }
        |
        |    /// FlowCollector protocol implementation
        |    public func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        |        if let typedValue = value as? T {
        |            onEmit(typedValue)
        |        }
        |        completionHandler(nil)
        |    }
        |}
        |
        |/// Extension for handling nullable Kotlin results
        |extension Optional {
        |    /// Unwrap or throw an error
        |    func unwrap(or error: Error = SwiftifyError.nullResult) throws -> Wrapped {
        |        guard let value = self else {
        |            throw error
        |        }
        |        return value
        |    }
        |}
        |
        |/// Swiftify-specific errors
        |public enum SwiftifyError: Error, LocalizedError {
        |    case nullResult
        |    case kotlinException(message: String)
        |
        |    public var errorDescription: String? {
        |        switch self {
        |        case .nullResult:
        |            return "Unexpected null result from Kotlin"
        |        case .kotlinException(let message):
        |            return "Kotlin exception: \(message)"
        |        }
        |    }
        |}
        """.trimMargin()

    /**
     * The filename for the runtime support.
     */
    const val FILENAME = "SwiftifyRuntime.swift"
}
