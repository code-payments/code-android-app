package com.getcode.opencode.internal.bidi

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

fun <Request, Response, StreamRef> openBidirectionalStream(
    streamRef: StreamRef,
    apiCall: (Flow<Request>) -> Flow<Response>,
    initialRequest: () -> Request,
    responseHandler: suspend (Response, sendRequest: (Request) -> Unit) -> Unit,
    onError: (Throwable) -> Unit = { trace(message = "Stream error: ${it.message}", type = TraceType.Error) },
    onComplete: () -> Unit = {},
    // Reconnect settings
    reconnectOnUnavailable: Boolean = false,
    reconnectOnDeadlineExceeded: Boolean = false,
    reconnectOnCancelled: Boolean = false,
    reconnectDelayMs: Long = 300L,
    maxReconnectAttempts: Int = 8,
    onReconnectAttempt: ((attempt: Int, reason: Throwable?) -> Unit)? = null
) where StreamRef : BidirectionalStreamReference<*, *> {

    val tag = streamRef.instanceLabel.takeIf { it.isNotBlank() }

    // Clean up any previous stream
    streamRef.cancel()

    val requestChannel = Channel<Request>(capacity = Channel.RENDEZVOUS)
    val requestFlow = requestChannel.consumeAsFlow()

    // Make sure we close the channel when stream completes/cancels
    streamRef.complete {
        trace(tag = tag, message = "Stream completed → closing request channel")
        requestChannel.close()
        onComplete()
    }

    var attemptConnection: suspend (Int) -> Boolean = { _ -> false } // placeholder

    fun shouldReconnect(cause: Throwable): Boolean {
        val statusException = cause as? StatusRuntimeException
        val statusCode = statusException?.status?.code

        return when {
            reconnectOnDeadlineExceeded && statusCode == Status.Code.DEADLINE_EXCEEDED -> {
                trace("Reconnecting stream due to DEADLINE_EXCEEDED status...")
                true
            }
            reconnectOnCancelled && statusCode == Status.Code.CANCELLED -> {
                trace("Reconnecting stream due to CANCELLED status...")
                true
            }
            reconnectOnUnavailable && statusCode == Status.Code.UNAVAILABLE -> {
                trace("Reconnecting stream due to UNAVAILABLE status...")
                true
            }
            else -> {
                false
            }
        }
    }

    suspend fun handleReconnectOrFail(attempt: Int, cause: Throwable): Boolean {
        if (!shouldReconnect(cause)) {
            trace(
                tag = tag,
                message = "Not reconnecting - unhandled error",
                type = TraceType.Error,
                error = cause
            )
            onError(cause)
            return false
        }

        onReconnectAttempt?.invoke(attempt, cause)
        trace(
            tag = tag,
            message = "Scheduling reconnect in ${reconnectDelayMs}ms (attempt $attempt)"
        )

        delay(reconnectDelayMs)

        // Recursive reconnect attempt
        return attemptConnection(attempt + 1)
    }

    fun handleStreamError(attempt: Int, cause: Throwable) {
        val status = (cause as? StatusRuntimeException)?.status
        val code = status?.code

        val shouldRetry = when {
            shouldReconnect(cause) -> true
            code == Status.Code.UNAVAILABLE -> true
            code == Status.Code.DEADLINE_EXCEEDED -> true
            code == Status.Code.CANCELLED -> true
            cause is CancellationException -> true
            else -> false
        }

        if (shouldRetry) {
            trace(
                tag = tag,
                message = "Stream failed (${code?.name ?: cause.javaClass.simpleName}) → reconnecting"
            )
            streamRef.coroutineScope.launch {
                handleReconnectOrFail(attempt, cause)
            }
        } else {
            trace(
                tag = tag,
                message = "Stream failed permanently",
                type = TraceType.Error,
                error = cause
            )
            onError(cause)
        }
    }

    attemptConnection = attempt@{ attempt: Int ->
        if (attempt > maxReconnectAttempts) {
            trace(
                tag = tag,
                message = "Giving up after $maxReconnectAttempts attempts",
                type = TraceType.Error
            )
            onError(IllegalStateException("Max reconnect attempts reached"))
            return@attempt false
        }

        trace(tag = tag, message = "Opening bidirectional stream (attempt $attempt)")

        val responseFlow = try {
            apiCall(requestFlow)
        } catch (e: Exception) {
            trace(
                tag = tag,
                message = "Failed to create stream flow",
                type = TraceType.Error,
                error = e
            )
            return@attempt handleReconnectOrFail(attempt, e)
        }

        streamRef.activateStream()

        val collectionJob = streamRef.coroutineScope.launch {
            responseFlow
                .catch { cause -> handleStreamError(attempt, cause) }
                .collect { response ->
                    responseHandler(response) { request ->
                        streamRef.coroutineScope.launch {
                            try {
                                requestChannel.send(request)
                            } catch (e: Exception) {
                                trace(
                                    tag = tag,
                                    message = "Failed to send follow-up request",
                                    type = TraceType.Error,
                                    error = e
                                )
                            }
                        }
                    }
                }
        }

        try {
            trace(tag = tag, message = "Sending initial request")
            streamRef.postponeTimeout()
            requestChannel.send(initialRequest())
            trace(tag = tag, message = "Initial request sent")
        } catch (e: Exception) {
            trace(
                tag = tag,
                message = "Failed to send initial request",
                type = TraceType.Error,
                error = e
            )
            collectionJob.cancel()
            return@attempt handleReconnectOrFail(attempt, e)
        }

        true
    }

    // Start the first connection attempt
    streamRef.coroutineScope.launch {
        attemptConnection(1)
    }
}