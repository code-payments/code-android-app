package com.getcode.opencode.internal.bidi

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BidirectionalStreamReference<Request, Response>(
    scope: CoroutineScope,
    val instanceLabel: String = ""
) : AutoCloseable {

    // supervisorJob must be THE Job of coroutineScope so destroy()/cancel() can
    // tear down every coroutine launched on this reference. CoroutineContext.plus
    // lets the right operand win on key collision, so supervisorJob has to come
    // LAST — otherwise scope's own Job overrides it and the reconnect loop is
    // launched under the outer scope instead, leaving destroy() to cancel an
    // orphaned job while the loop keeps reconnecting (the server's
    // "ABORTED: stream already exists" storm). Parenting it to scope's Job keeps
    // the reference tied to the outer lifecycle (scope cancel → ref cancel).
    private val supervisorJob = SupervisorJob(scope.coroutineContext[Job])
    val coroutineScope = CoroutineScope(scope.coroutineContext + supervisorJob)

    val isActive: Boolean get() = isStreamActive

    private var isStreamActive: Boolean = false
        set(value) {
            if (field != value) {
                trace(
                    tag = "BIDI",
                    type = TraceType.StateChange,
                    message = "$instanceLabel Stream active: $value, previous: $field",
                )
            }
            field = value
            if (value) {
                postponeTimeout()
            } else {
                cancelTimeout()
            }
        }

    var timeoutHandler: (() -> Unit)? = null

    var onConnect: (() -> Unit)? = null

    private var lastPing: Long? = null

    private var pingTimeout = 15_000L

    private var closure: (() -> BidirectionalStreamReference<Request, Response>)? = null

    private var timeoutTask: Job? = null

    override fun close() {
        trace(
            tag = "BIDI",
            type = TraceType.StateChange,
            message = "Deallocating bidirectional $instanceLabel stream reference",
        )
    }

    fun receivedPing(updatedTimeout: Long? = null) {
        if (lastPing == null) {
            onConnect?.invoke()
        }

        lastPing = System.currentTimeMillis()

        updatedTimeout?.let {
            val newTimeout = (it * 2)
            if (pingTimeout != newTimeout) {
                trace(
                    tag = "BIDI",
                    type = TraceType.StateChange,
                    message = "$instanceLabel Updating timeout from $pingTimeout to $newTimeout",
                )
                pingTimeout = newTimeout
            }
        }

        postponeTimeout()
    }

    private fun cancelTimeout() {
        timeoutTask?.cancel()
        timeoutTask = null
    }

    internal fun postponeTimeout() {
        cancelTimeout()
        timeoutTask = coroutineScope.launch {
            if (!isActive) return@launch

            delay(pingTimeout)
            if (isActive) {
                timeoutHandler?.invoke()
            }
        }
    }

    fun destroy() {
        lastPing = null
        timeoutHandler = null
        onConnect = null
        isStreamActive = false
        cancelTimeout()
        supervisorJob.cancel()
        release()
    }

    fun cancel() {
        if (isStreamActive) {
            trace(
                tag = "BIDI",
                type = TraceType.StateChange,
                message = "Cancelling $instanceLabel stream"
            )
            try {
                coroutineScope.cancel("$instanceLabel Stream cancelled")
                isStreamActive = false
                trace(
                    tag = "BIDI",
                    type = TraceType.StateChange,
                    message = "$instanceLabel Stream cancelled"
                )
            } catch (e: Exception) {
                trace(
                    tag = "BIDI",
                    message = "Error cancelling $instanceLabel stream: ${e.message}",
                    type = TraceType.Error
                )
            }
        }
    }

    fun complete(completionHandler: () -> Unit = { }) {
        if (isStreamActive) {
            trace(
                tag = "BIDI",
                type = TraceType.StateChange,
                message = "Completing $instanceLabel stream"
            )
            try {
                completionHandler()
                isStreamActive = false
                cancelTimeout()
                trace(
                    tag = "BIDI",
                    type = TraceType.StateChange,
                    message = "$instanceLabel stream completed",
                )
            } catch (e: Exception) {
                trace(
                    tag = "BIDI",
                    type = TraceType.Error,
                    message = "Error completing $instanceLabel stream: ${e.message}"
                )
            }
        }
    }

    fun retain() {
        closure = { this }
    }

    private fun release() {
        closure = null
    }

    fun activateStream() {
        isStreamActive = true
    }
}