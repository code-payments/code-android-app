package com.getcode.opencode.internal.bidi

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
    reconnectOnAborted: Boolean = false,
    reconnectDelayMs: Long = 300L,
    maxReconnectDelayMs: Long = 30_000L,
    maxReconnectAttempts: Int = 8,
    // Minimum time a stream must stay connected (past first response) before we
    // treat it as healthy and reset the backoff counter. Prevents an
    // activate-then-immediately-fail stream from resetting forever.
    healthyThreshold: Duration = 30.seconds,
    timeSource: TimeSource = TimeSource.Monotonic,
    onReconnectAttempt: ((attempt: Int, reason: Throwable?) -> Unit)? = null
) where StreamRef : BidirectionalStreamReference<*, *> {

    val tag = streamRef.instanceLabel.takeIf { it.isNotBlank() }

    // Clean up any previous stream
    streamRef.cancel()

    fun isRetryable(cause: Throwable): Boolean {
        val code = cause.grpcStatusCode()
        return when {
            reconnectOnUnavailable && code == Status.Code.UNAVAILABLE -> true
            reconnectOnDeadlineExceeded && code == Status.Code.DEADLINE_EXCEEDED -> true
            reconnectOnCancelled && code == Status.Code.CANCELLED -> true
            reconnectOnAborted && code == Status.Code.ABORTED -> true
            else -> false
        }
    }

    // Single coordinator loop — no competing reconnect chains.
    // Errors signal this channel; the loop decides whether to retry.
    val retrySignal = Channel<Throwable>(capacity = Channel.CONFLATED)

    streamRef.coroutineScope.launch {
        var attempt = 0

        while (attempt++ <= maxReconnectAttempts) {
            val backoffMs = computeBackoffMs(attempt, reconnectDelayMs, maxReconnectDelayMs)
            if (backoffMs > 0) {
                delay(backoffMs)
            }

            trace(tag = tag, message = "Opening bidirectional stream (attempt $attempt)")

            // Fresh channel per attempt — consumeAsFlow() is single-use
            val requestChannel = Channel<Request>(capacity = Channel.RENDEZVOUS)
            val requestFlow = requestChannel.consumeAsFlow()

            val responseFlow = try {
                apiCall(requestFlow)
            } catch (e: Exception) {
                trace(
                    tag = tag,
                    message = "Failed to create stream flow",
                    type = TraceType.Error,
                    error = e
                )
                requestChannel.close()
                if (isRetryable(e)) {
                    onReconnectAttempt?.invoke(attempt, e)
                    continue
                }
                onError(e)
                return@launch
            }

            var activated = false
            var activationMark: TimeMark? = null

            val collectionJob = launch {
                responseFlow
                    .catch { cause ->
                        requestChannel.close()
                        if (cause !is CancellationException) {
                            retrySignal.trySend(cause)
                        }
                    }
                    .collect { response ->
                        if (!activated) {
                            activated = true
                            activationMark = timeSource.markNow()
                            streamRef.activateStream()
                            trace(tag = tag, message = "Stream activated on first response")
                        }
                        responseHandler(response) { request ->
                            launch {
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
                collectionJob.cancel()
                requestChannel.close()
                if (e is ClosedSendChannelException) {
                    // Expected race: the response flow can close the request
                    // channel (e.g. gRPC TRANSIENT_FAILURE) before the initial
                    // send completes. Retry silently.
                    trace(tag = tag, message = "Channel closed before initial request, retrying")
                    onReconnectAttempt?.invoke(attempt, e)
                    continue
                }
                trace(
                    tag = tag,
                    message = "Failed to send initial request",
                    type = TraceType.Error,
                    error = e
                )
                if (isRetryable(e)) {
                    onReconnectAttempt?.invoke(attempt, e)
                    continue
                }
                onError(e)
                return@launch
            }

            // Wait for the collection to fail (retry signal) or complete normally.
            // If the stream is healthy, this suspends indefinitely until an error occurs.
            val error = retrySignal.receive()
            collectionJob.cancel()
            requestChannel.close()

            val code = error.grpcStatusCode()
            if (isRetryable(error)) {
                val healthy = activationMark?.elapsedNow()?.let { it >= healthyThreshold } ?: false
                trace(
                    tag = tag,
                    message = "Stream failed (${code?.name ?: error.javaClass.simpleName}) " +
                        "→ reconnecting (attempt $attempt, healthy=$healthy)"
                )
                onReconnectAttempt?.invoke(attempt, error)
                // Reset the backoff counter ONLY when the stream was genuinely
                // healthy (stayed connected past healthyThreshold). A stream that
                // activates then immediately fails (e.g. ABORTED "stream already
                // exists") must count toward maxReconnectAttempts so the loop
                // terminates instead of hammering the server.
                // Reset to 0: after a healthy stream dies, reconnect immediately
                // (computeBackoffMs returns 0 for attempt=1).
                if (healthy) attempt = 0
                continue
            }

            trace(
                tag = tag,
                message = "Stream failed permanently",
                type = TraceType.Error,
                error = error
            )
            onError(error)
            return@launch
        }

        trace(
            tag = tag,
            message = "Giving up after $maxReconnectAttempts attempts",
            type = TraceType.Error
        )
        onError(IllegalStateException("Max reconnect attempts reached"))
    }
}

internal fun Throwable.grpcStatusCode(): Status.Code? = when (this) {
    is StatusRuntimeException -> status.code
    is StatusException -> status.code
    else -> null
}

/**
 * Exponential backoff with a hard cap. Returns 0 for the first attempt (no delay)
 * and for a zero base delay (used in tests). Doubles per consecutive failed attempt.
 */
internal fun computeBackoffMs(attempt: Int, baseDelayMs: Long, maxDelayMs: Long): Long {
    if (attempt <= 1 || baseDelayMs <= 0) return 0L
    val exponent = (attempt - 2).coerceIn(0, 16)
    val scaled = baseDelayMs shl exponent
    return scaled.coerceAtMost(maxDelayMs)
}
