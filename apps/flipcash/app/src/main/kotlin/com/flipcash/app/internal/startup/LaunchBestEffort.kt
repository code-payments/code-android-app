package com.flipcash.app.internal.startup

import com.getcode.utils.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [block] off the startup thread as a best-effort side task: if it throws, the failure is
 * reported through [onFailure] and the process survives.
 *
 * `androidx.startup` initializers run during `ContentProvider` creation, before any UI exists. A
 * bare `CoroutineScope(Dispatchers.IO).launch { }` there has no exception handler, so anything the
 * block throws reaches the default uncaught-exception handler and kills a cold start outright — see
 * Bugsnag `6a8f47a7b5ee91bed8ac6cac`, where CameraX's `Context.getDeviceId()` call threw
 * `NoSuchMethodError` on a runtime that misreports its API level.
 *
 * Catches [Throwable], not [Exception]: `NoSuchMethodError` and the rest of `LinkageError` extend
 * `Error`. [CancellationException] is re-thrown so structured cancellation still works.
 *
 * @param tag short trace tag identifying the startup task, e.g. `"camerax"`.
 * @param dispatcher the dispatcher to run on; overridable for tests.
 * @param onFailure what to do with a non-cancellation throwable. The default traces it, which also
 *   files it as a handled error via `ErrorUtils`.
 */
internal fun launchBestEffort(
    tag: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    onFailure: (String, Throwable) -> Unit = ::traceStartupFailure,
    block: suspend CoroutineScope.() -> Unit,
): Job = CoroutineScope(dispatcher).launch {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        onFailure(tag, throwable)
    }
}

/**
 * Default [launchBestEffort] failure sink.
 *
 * Note that [trace] no-ops until `TraceManager.initialize` has run, which is why the initializers
 * that use [launchBestEffort] declare `TraceInitializer` as a dependency.
 */
internal fun traceStartupFailure(tag: String, error: Throwable) {
    trace(
        message = "Startup task failed",
        tag = tag,
        error = error,
    )
}
