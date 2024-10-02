package com.getcode.network.core

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber


class BidirectionalStreamReference<Request, Response>(private val scope: CoroutineScope) : AutoCloseable {

    var stream: StreamObserver<Request>? = null
        set(value) {
            field = value
            if (value != null) {
                postponeTimeout()
            } else {
                cancelTimeout()
            }
        }

    var timeoutHandler: (() -> Unit)? = null

    private var lastPing: Long? = null

    private var pingTimeout = 15_000L

    private var closure: (() -> BidirectionalStreamReference<Request, Response>)? = null

    private var timeoutTask: Job? = null

    override fun close() {
        Timber.d("Deallocating bidirectional stream reference:")
    }

    fun receivedPing(updatedTimeout: Long? = null) {
        lastPing = System.currentTimeMillis()

        // if the server provides a timeout, we'll update our local timeout accordingly.
        updatedTimeout?.let {
            // double provided timeout
            val newTimeout = (it * 2)
            if (pingTimeout != newTimeout) {
                trace(type = TraceType.StateChange, message = "Updating timeout from $pingTimeout to $newTimeout")
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
        timeoutTask = scope.launch {
            if (!isActive) return@launch

            delay(pingTimeout)
            if (isActive) {
                timeoutHandler?.invoke()
            }
        }
    }

    fun destroy() {
        timeoutHandler = null
        cancelTimeout()
        cancel()
        release()
    }

    fun cancel() {
        stream?.onCompleted()
    }

    fun retain() {
        closure = { this }
    }

    fun release() {
        closure = null
    }
}