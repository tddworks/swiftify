import Foundation

/// Protocol for Kotlin Flow wrappers.
/// Implemented by generated code to bridge Kotlin Flow to Swift AsyncSequence.
public protocol SwiftifyFlowWrapper {
    associatedtype Element

    /// Collect the flow with callbacks.
    func collect(
        onEach: @escaping (Element) -> Void,
        onComplete: @escaping () -> Void,
        onError: @escaping (Error) -> Void
    ) -> SwiftifyCancellationToken
}

/// Bridges a Kotlin Flow to Swift AsyncStream.
///
/// Usage:
/// ```swift
/// let stream = flowBridge { onEach, onComplete, onError in
///     KotlinClass().someFlow().collect(
///         onEach: onEach,
///         onComplete: onComplete,
///         onError: onError
///     )
/// }
///
/// for await item in stream {
///     print(item)
/// }
/// ```
public func flowBridge<T>(
    _ collector: @escaping (
        _ onEach: @escaping (T) -> Void,
        _ onComplete: @escaping () -> Void,
        _ onError: @escaping (Error) -> Void
    ) -> SwiftifyCancellationToken
) -> AsyncStream<T> {
    AsyncStream { continuation in
        let token = collector(
            { element in
                continuation.yield(element)
            },
            {
                continuation.finish()
            },
            { error in
                // AsyncStream doesn't support errors, so we just finish
                continuation.finish()
            }
        )

        continuation.onTermination = { _ in
            token.cancel()
        }
    }
}

/// Bridges a Kotlin Flow to Swift AsyncThrowingStream (with error support).
public func flowBridgeThrowing<T>(
    _ collector: @escaping (
        _ onEach: @escaping (T) -> Void,
        _ onComplete: @escaping () -> Void,
        _ onError: @escaping (Error) -> Void
    ) -> SwiftifyCancellationToken
) -> AsyncThrowingStream<T, Error> {
    AsyncThrowingStream { continuation in
        let token = collector(
            { element in
                continuation.yield(element)
            },
            {
                continuation.finish()
            },
            { error in
                continuation.finish(throwing: error)
            }
        )

        continuation.onTermination = { _ in
            token.cancel()
        }
    }
}

/// Bridges a Kotlin StateFlow to a Swift property with current value and updates.
public class SwiftifyStateFlowBridge<T> {
    private var currentValue: T
    private var observers: [(T) -> Void] = []
    private let lock = NSLock()

    public init(initialValue: T) {
        self.currentValue = initialValue
    }

    public var value: T {
        lock.lock()
        defer { lock.unlock() }
        return currentValue
    }

    public func update(_ newValue: T) {
        lock.lock()
        currentValue = newValue
        let currentObservers = observers
        lock.unlock()

        for observer in currentObservers {
            observer(newValue)
        }
    }

    public func observe(_ handler: @escaping (T) -> Void) -> SwiftifyCancellationToken {
        lock.lock()
        observers.append(handler)
        let currentValue = self.currentValue
        lock.unlock()

        // Emit current value immediately
        handler(currentValue)

        let token = SwiftifyCancellationToken()
        token.onCancel { [weak self] in
            self?.removeObserver(handler)
        }
        return token
    }

    private func removeObserver(_ handler: @escaping (T) -> Void) {
        lock.lock()
        // Note: This is a simplified implementation
        // In production, use identifiable observers
        lock.unlock()
    }

    /// Get updates as an AsyncStream
    public var updates: AsyncStream<T> {
        AsyncStream { [weak self] continuation in
            guard let self = self else {
                continuation.finish()
                return
            }

            let token = self.observe { value in
                continuation.yield(value)
            }

            continuation.onTermination = { _ in
                token.cancel()
            }
        }
    }
}

/// Bridges a Kotlin SharedFlow to Swift AsyncStream with replay support.
public class SwiftifySharedFlowBridge<T> {
    private var replayBuffer: [T] = []
    private let replayCount: Int
    private var observers: [(T) -> Void] = []
    private let lock = NSLock()

    public init(replayCount: Int = 0) {
        self.replayCount = replayCount
    }

    public func emit(_ value: T) {
        lock.lock()

        if replayCount > 0 {
            replayBuffer.append(value)
            if replayBuffer.count > replayCount {
                replayBuffer.removeFirst()
            }
        }

        let currentObservers = observers
        lock.unlock()

        for observer in currentObservers {
            observer(value)
        }
    }

    public func subscribe(_ handler: @escaping (T) -> Void) -> SwiftifyCancellationToken {
        lock.lock()
        observers.append(handler)
        let replay = replayBuffer
        lock.unlock()

        // Emit replayed values
        for value in replay {
            handler(value)
        }

        let token = SwiftifyCancellationToken()
        token.onCancel { [weak self] in
            self?.removeObserver(handler)
        }
        return token
    }

    private func removeObserver(_ handler: @escaping (T) -> Void) {
        lock.lock()
        // Simplified implementation
        lock.unlock()
    }

    /// Get emissions as an AsyncStream
    public var emissions: AsyncStream<T> {
        AsyncStream { [weak self] continuation in
            guard let self = self else {
                continuation.finish()
                return
            }

            let token = self.subscribe { value in
                continuation.yield(value)
            }

            continuation.onTermination = { _ in
                token.cancel()
            }
        }
    }
}
