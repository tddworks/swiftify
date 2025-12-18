package io.swiftify.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Bridge for collecting Kotlin Flow from Swift.
 *
 * This provides the Kotlin side of the Flow-to-AsyncSequence bridge.
 */
object FlowBridge {

    /**
     * Default dispatcher for flow collection.
     */
    var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Dispatcher for callbacks. Defaults to Main dispatcher.
     */
    var callbackDispatcher: CoroutineDispatcher = Dispatchers.Main

    /**
     * Collects a Flow and calls handlers for each emission.
     *
     * @param flow The Flow to collect
     * @param onEach Called for each emitted value
     * @param onComplete Called when the flow completes
     * @param onError Called if an error occurs
     * @return A Cancellable that can be used to cancel collection
     */
    fun <T> collect(
        flow: Flow<T>,
        onEach: (T) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        val scope = CoroutineScope(defaultDispatcher + SupervisorJob())

        val job = scope.launch {
            try {
                flow.collect { value ->
                    withContext(callbackDispatcher) {
                        onEach(value)
                    }
                }
                withContext(callbackDispatcher) {
                    onComplete()
                }
            } catch (e: CancellationException) {
                // Cancelled, don't call handlers
                throw e
            } catch (e: Throwable) {
                withContext(callbackDispatcher) {
                    onError(e)
                }
            }
        }

        return CancellableImpl(job)
    }
}

/**
 * Wrapper class that exposes a Flow to Swift.
 */
class SwiftFlowCollector<T>(private val flow: Flow<T>) {

    /**
     * Start collecting from Swift with callbacks.
     */
    fun collect(
        onEach: (T) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        return FlowBridge.collect(flow, onEach, onComplete, onError)
    }
}

/**
 * Extension to convert a Flow to a Swift-callable collector.
 */
fun <T> Flow<T>.toSwiftCollector(): SwiftFlowCollector<T> {
    return SwiftFlowCollector(this)
}

/**
 * Wrapper for StateFlow that exposes current value and updates to Swift.
 */
class SwiftStateFlowWrapper<T>(private val stateFlow: StateFlow<T>) {

    /**
     * Get the current value.
     */
    val value: T
        get() = stateFlow.value

    /**
     * Collect updates.
     */
    fun collect(
        onEach: (T) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        return FlowBridge.collect(stateFlow, onEach, onComplete, onError)
    }
}

/**
 * Extension to convert a StateFlow to a Swift wrapper.
 */
fun <T> StateFlow<T>.toSwiftWrapper(): SwiftStateFlowWrapper<T> {
    return SwiftStateFlowWrapper(this)
}

/**
 * Wrapper for SharedFlow with replay support.
 */
class SwiftSharedFlowWrapper<T>(private val sharedFlow: SharedFlow<T>) {

    /**
     * Get the replay cache.
     */
    val replayCache: List<T>
        get() = sharedFlow.replayCache

    /**
     * Collect emissions.
     */
    fun collect(
        onEach: (T) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        return FlowBridge.collect(sharedFlow, onEach, onComplete, onError)
    }
}

/**
 * Extension to convert a SharedFlow to a Swift wrapper.
 */
fun <T> SharedFlow<T>.toSwiftWrapper(): SwiftSharedFlowWrapper<T> {
    return SwiftSharedFlowWrapper(this)
}
