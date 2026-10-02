package com.flipcash.app.internal.startup

import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import com.getcode.utils.BreadcrumbSink
import com.getcode.utils.TraceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Forwards breadcrumbs to Bugsnag from one background worker, never from the thread that traced.
 *
 * `trace()` runs every sink on the caller's thread, and `Bugsnag.leaveBreadcrumb` can block: on
 * 2026.9.2 Play reported the main thread stuck in it for 10 s+ while handling FCM pushes. Queueing
 * here keeps `trace()` on the main thread from ever waiting on Bugsnag.
 *
 * One worker keeps breadcrumbs in order. The queue holds [capacity] and drops the oldest when full,
 * so a stuck worker can't grow it; the default matches Bugsnag's own 100-breadcrumb limit, which
 * would discard the same ones. Bugsnag timestamps a breadcrumb when the worker delivers it, normally
 * microseconds after it was traced. The worker has its own thread because the shared IO pool is
 * saturated during cold start, which is when these ANRs happen.
 *
 * [TraceType.Error] is the exception: it goes to Bugsnag on the caller's thread. `trace()` reports
 * the error right after the sinks run, and the report snapshots breadcrumbs at that point, so a
 * queued crumb would be missing from its own report. This adds no new way to block: the report
 * that follows leaves its own breadcrumb on the same thread anyway. It can land ahead of crumbs
 * still in the queue.
 */
class BugsnagBreadcrumbSink(
    dispatcher: CoroutineDispatcher = newWorkerDispatcher(),
    capacity: Int = DEFAULT_CAPACITY,
    private val leave: (String, Map<String, Any>, BreadcrumbType) -> Unit = ::leaveBugsnagBreadcrumb,
) : BreadcrumbSink {

    private class Pending(val message: String, val metadata: Map<String, Any>, val type: BreadcrumbType)

    private val pending = Channel<Pending>(capacity, BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (crumb in pending) deliver(crumb.message, crumb.metadata, crumb.type)
        }
    }

    override fun record(message: String, metadata: Map<String, Any>, type: TraceType) {
        val breadcrumbType = type.toBugsnagBreadcrumbType() ?: return
        if (breadcrumbType == BreadcrumbType.ERROR) {
            deliver(message, metadata, breadcrumbType)
        } else {
            pending.trySend(Pending(message, metadata, breadcrumbType))
        }
    }

    private fun deliver(message: String, metadata: Map<String, Any>, type: BreadcrumbType) {
        // Can't trace a failure here: it would come straight back into this sink.
        runCatching { leave(message, metadata, type) }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 100
    }
}

private fun newWorkerDispatcher(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bugsnag-breadcrumbs").apply { isDaemon = true }
    }.asCoroutineDispatcher()

private fun leaveBugsnagBreadcrumb(message: String, metadata: Map<String, Any>, type: BreadcrumbType) {
    if (!Bugsnag.isStarted()) return
    Bugsnag.leaveBreadcrumb(message, metadata, type)
}

private fun TraceType.toBugsnagBreadcrumbType(): BreadcrumbType? {
    return when (this) {
        TraceType.Silent -> null
        TraceType.Error -> BreadcrumbType.ERROR
        TraceType.Log -> BreadcrumbType.LOG
        TraceType.Navigation -> BreadcrumbType.NAVIGATION
        TraceType.Network -> BreadcrumbType.REQUEST
        TraceType.Process -> BreadcrumbType.PROCESS
        TraceType.StateChange -> BreadcrumbType.STATE
        TraceType.User -> BreadcrumbType.USER
    }
}
