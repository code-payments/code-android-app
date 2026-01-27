package com.getcode.opencode.internal.bidi

import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

fun <Request, Response, StreamRef : BidirectionalStreamReference<*, *>> openBidirectionalStream(
    streamRef: StreamRef,
    apiCall: (Flow<Request>) -> Flow<Response>,
    initialRequest: () -> Request,
    onError: (Throwable) -> Unit = { ErrorUtils.handleError(it) },
    onComplete: () -> Unit = {},
    reconnectOnUnavailable: Boolean = false,
    reconnectOnDeadlineExceeded: Boolean = false,
    reconnectOnCancelled: Boolean = false,
    reconnectHandler: (() -> Unit)? = null,
    responseHandler: suspend (response: Response, requestChannel: (Request) -> Unit) -> Unit
) {
    val tag = streamRef.instanceLabel.ifEmpty { null }

    streamRef.cancel()

    val requestChannel = Channel<Request>(capacity = 1)
    val requestFlow = requestChannel.consumeAsFlow()

    streamRef.complete {
        trace(tag = tag, message = "Closing request channel")
        requestChannel.close()
        onComplete()
    }

    trace(tag = tag, message = "Opening stream")
    val responseFlow = apiCall(requestFlow)

    streamRef.activateStream()

    streamRef.coroutineScope.launch {
        responseFlow.catch { t ->
            val statusException = t as? StatusRuntimeException
            val statusCode = statusException?.status?.code
            when {
                reconnectOnDeadlineExceeded && statusCode == Status.Code.DEADLINE_EXCEEDED -> {
                    trace(tag = tag, message = "Reconnecting stream due to DEADLINE_EXCEEDED status...")
                    reconnectHandler?.invoke()
                }
                reconnectOnCancelled && statusCode == Status.Code.CANCELLED -> {
                    trace(tag = tag, message = "Reconnecting stream due to CANCELLED status...")
                    reconnectHandler?.invoke()
                }
                reconnectOnUnavailable && statusCode == Status.Code.UNAVAILABLE -> {
                    trace(tag = tag, message ="Reconnecting stream due to UNAVAILABLE status...")
                    reconnectHandler?.invoke()
                }
                else -> {
                    trace(tag = tag, message ="Stream error: ${t.message}", type = TraceType.Error)
                    onError(t)
                }
            }
        }.collect { value ->
            responseHandler(value) { request ->
                streamRef.coroutineScope.launch {
                    requestChannel.send(request)
                }
            }
        }
    }

    streamRef.coroutineScope.launch {
        trace(tag = tag, message = "Preparing to send initial request")
        if (requestChannel.isClosedForSend) {
            trace(message = "Request channel closed before sending initial request", type = TraceType.Error)
            return@launch
        }
        val request = initialRequest()
        streamRef.postponeTimeout()
        val sendResult = requestChannel.trySend(request)
        if (sendResult.isSuccess) {
            trace(tag = tag, message = "Initial request sent successfully")
        } else {
            trace(tag = tag, message = "Initial request send failed: $sendResult", type = TraceType.Error)
            try {
                requestChannel.send(request)
                trace("Initial request sent successfully (fallback send)")
            } catch (e: Exception) {
                trace(tag = tag, message = "Failed to send initial request: ${e.message}", type = TraceType.Error)
                onError(e)
            }
        }
    }
}