package com.getcode.opencode.observers

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber


class BidirectionalStreamReference<Request, Response>(scope: CoroutineScope) : AutoCloseable {

    private val supervisorJob = SupervisorJob()
    val coroutineScope = CoroutineScope(supervisorJob + scope.coroutineContext)

    var stream: StreamObserver<Request>? = null
        set(value) {
            Timber.d("Setting stream: ${value.hashCode()}, previous: ${field.hashCode()}")
            field = value
            if (value != null) {
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
        Timber.d("Deallocating bidirectional stream reference:")
    }

    fun receivedPing(updatedTimeout: Long? = null) {
        if (lastPing == null) {
            onConnect?.invoke()
        }

        lastPing = System.currentTimeMillis()

        // if the server provides a timeout, we'll update our local timeout accordingly.
        updatedTimeout?.let {
            // double provided timeout
            val newTimeout = (it * 2)
            if (pingTimeout != newTimeout) {
                trace(
                    type = TraceType.StateChange,
                    message = "Updating timeout from $pingTimeout to $newTimeout"
                )
                pingTimeout = newTimeout
            }
        }

        postponeTimeout()
    }

    fun cancelTimeout() {
        timeoutTask?.cancel()
        timeoutTask = null
    }

    fun postponeTimeout() {
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
        cancelTimeout()
        cancel()
        release()
    }

    fun cancel() {
        Timber.d("Cancelling stream: $stream")
        stream?.onCompleted()
        stream = null
    }

    fun retain() {
        closure = { this }
    }

    fun release() {
        closure = null
    }
}