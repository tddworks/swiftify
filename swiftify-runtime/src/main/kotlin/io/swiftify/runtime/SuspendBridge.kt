package io.swiftify.runtime

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Bridge for calling Kotlin suspend functions from Swift.
 *
 * This provides the Kotlin side of the suspend-to-async bridge.
 * The Swift side calls these methods with completion handlers.
 */
object SuspendBridge {

    /**
     * Default dispatcher for suspend function execution.
     * Can be overridden for testing.
     */
    var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Launches a suspend function and calls the completion handler when done.
     *
     * @param block The suspend function to execute
     * @param onSuccess Called with the result on success
     * @param onError Called with the exception on failure
     * @return A Cancellable that can be used to cancel the operation
     */
    fun <T> launch(
        block: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        val scope = CoroutineScope(defaultDispatcher + SupervisorJob())

        val job = scope.launch {
            try {
                val result = block()
                withContext(Dispatchers.Main) {
                    onSuccess(result)
                }
            } catch (e: CancellationException) {
                // Cancelled, don't call error handler
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }

        return CancellableImpl(job)
    }

    /**
     * Launches a suspend function that returns Unit.
     */
    fun launchUnit(
        block: suspend () -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        return launch(
            block = block,
            onSuccess = { onComplete() },
            onError = onError
        )
    }
}

/**
 * Interface for cancellable operations.
 */
interface Cancellable {
    fun cancel()
    val isCancelled: Boolean
}

/**
 * Implementation of Cancellable backed by a coroutine Job.
 */
internal class CancellableImpl(private val job: Job) : Cancellable {
    override fun cancel() {
        job.cancel()
    }

    override val isCancelled: Boolean
        get() = job.isCancelled
}

/**
 * Extension function to convert a suspend function to a Swift-callable wrapper.
 *
 * Usage in Kotlin:
 * ```kotlin
 * val wrapper = ::fetchUser.toSwiftWrapper()
 * // wrapper can be called from Swift with completion handler
 * ```
 */
inline fun <T> (suspend () -> T).toSwiftCallable(): SwiftSuspendCallable<T> {
    return SwiftSuspendCallable(this)
}

/**
 * Wrapper class that exposes a suspend function to Swift.
 */
class SwiftSuspendCallable<T>(private val block: suspend () -> T) {

    /**
     * Call from Swift with completion handler.
     */
    fun invoke(
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancellable {
        return SuspendBridge.launch(block, onSuccess, onError)
    }
}
