import Foundation

/// Protocol for Kotlin suspend function wrappers.
/// Implemented by generated code to bridge Kotlin suspend functions to Swift async.
public protocol SwiftifySuspendWrapper {
    associatedtype Result

    /// Invoke the suspend function with a completion handler.
    func invoke(completion: @escaping (Result?, Error?) -> Void)

    /// Cancel the ongoing operation if possible.
    func cancel()
}

/// Bridges a Kotlin suspend function to Swift async/await.
///
/// Usage:
/// ```swift
/// let result = try await suspendBridge {
///     KotlinClass().someSuspendFunction(completion: $0)
/// }
/// ```
public func suspendBridge<T>(
    _ block: @escaping (@escaping (T?, Error?) -> Void) -> Void
) async throws -> T {
    try await withCheckedThrowingContinuation { continuation in
        block { result, error in
            if let error = error {
                continuation.resume(throwing: error)
            } else if let result = result {
                continuation.resume(returning: result)
            } else {
                continuation.resume(throwing: SwiftifyError.nullResult)
            }
        }
    }
}

/// Bridges a Kotlin suspend function that returns Unit to Swift async.
public func suspendBridgeUnit(
    _ block: @escaping (@escaping (Error?) -> Void) -> Void
) async throws {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
        block { error in
            if let error = error {
                continuation.resume(throwing: error)
            } else {
                continuation.resume()
            }
        }
    }
}

/// Bridges a Kotlin suspend function to Swift async without throwing.
public func suspendBridgeNonThrowing<T>(
    _ block: @escaping (@escaping (T?, Error?) -> Void) -> Void
) async -> T? {
    await withCheckedContinuation { continuation in
        block { result, _ in
            continuation.resume(returning: result)
        }
    }
}

/// Errors that can occur during Swiftify bridging.
public enum SwiftifyError: Error, LocalizedError {
    case nullResult
    case cancelled
    case unknown(String)

    public var errorDescription: String? {
        switch self {
        case .nullResult:
            return "Kotlin function returned null unexpectedly"
        case .cancelled:
            return "Operation was cancelled"
        case .unknown(let message):
            return message
        }
    }
}

/// Cancellation token for managing async operation lifecycle.
public class SwiftifyCancellationToken {
    private var isCancelledValue = false
    private let lock = NSLock()
    private var onCancel: (() -> Void)?

    public var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return isCancelledValue
    }

    public func cancel() {
        lock.lock()
        isCancelledValue = true
        let handler = onCancel
        lock.unlock()
        handler?()
    }

    public func onCancel(_ handler: @escaping () -> Void) {
        lock.lock()
        if isCancelledValue {
            lock.unlock()
            handler()
        } else {
            onCancel = handler
            lock.unlock()
        }
    }
}
