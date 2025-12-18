package io.swiftify.generator

/**
 * Generates the Swift runtime support code needed for Swiftify bridging.
 *
 * This includes helper classes like SwiftifyFlowCollector for Flow→AsyncStream bridging.
 */
object SwiftRuntimeSupport {

    /**
     * Generate the complete runtime support Swift code.
     */
    fun generate(): String = """
        |// MARK: - Swiftify Runtime Support
        |// Helper classes for Kotlin/Swift bridging
        |
        |import Foundation
        |
        |/// Collector for bridging Kotlin Flow to Swift AsyncStream
        |public class SwiftifyFlowCollector<T>: NSObject {
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
        |    public func emit(_ value: T) {
        |        onEmit(value)
        |    }
        |
        |    public func complete() {
        |        onComplete()
        |    }
        |
        |    public func error(_ error: Error) {
        |        onError(error)
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
