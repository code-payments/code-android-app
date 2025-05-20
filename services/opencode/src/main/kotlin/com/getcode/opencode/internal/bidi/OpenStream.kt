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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun <Request, Response, StreamRef : BidirectionalStreamReference<*, *>, ResultType> openBidirectionalStream(
    streamRef: StreamRef,
    apiCall: (Flow<Request>) -> Flow<Response>,
    initialRequest: () -> Request,
    onError: (Throwable) -> Unit = { ErrorUtils.handleError(it) },
    reconnectOnUnavailable: Boolean = false,
    reconnectOnDeadlineExceeded: Boolean = false,
    reconnectOnCancelled: Boolean = false,
    reconnectHandler: (() -> Unit)? = null,
    responseHandler: suspend (Response, (ResultType) -> Unit, (Request) -> Unit) -> Unit
): ResultType {
    return suspendCancellableCoroutine { cont ->
        try {
            streamRef.cancel()

            // Create a Channel for requests
            val requestChannel = Channel<Request>(capacity = 1)
            val requestFlow = requestChannel.consumeAsFlow()

            // Set up completion handler
            streamRef.complete {
                trace("Closing request channel")
                requestChannel.close()
            }

            // Open the stream
            trace("Opening stream")
            val responseFlow = apiCall(requestFlow)

            // Mark the stream as active
            streamRef.activateStream()

            // Handle responses
            streamRef.coroutineScope.launch {
                responseFlow.catch { t ->
                    val statusException = t as? StatusRuntimeException
                    val statusCode = statusException?.status?.code
                    when {
                        reconnectOnDeadlineExceeded && statusCode == Status.Code.DEADLINE_EXCEEDED -> {
                            trace("Reconnecting stream due to DEADLINE_EXCEEDED status...")
                            reconnectHandler?.invoke()
                        }
                        reconnectOnCancelled && statusCode == Status.Code.CANCELLED -> {
                            trace("Reconnecting stream due to CANCELLED status...")
                            reconnectHandler?.invoke()
                        }
                        reconnectOnUnavailable && statusCode == Status.Code.UNAVAILABLE -> {
                            trace("Reconnecting stream due to UNAVAILABLE status...")
                            reconnectHandler?.invoke()
                        } else -> {
                            if (reconnectOnUnavailable || reconnectOnDeadlineExceeded || reconnectOnCancelled) {
                                trace(
                                    message = "Stream error: ${t.message}",
                                    type = TraceType.Error
                                )
                                onError(t)
                                if (cont.isActive) {
                                    cont.resume(null as ResultType)
                                }
                            }
                        }
                    }
                }.collect { value ->
                    responseHandler(
                        value,
                        { result ->
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }, { request ->
                            streamRef.coroutineScope.launch {
                                requestChannel.send(request)
                            }
                        }
                    )
                }
            }

            // Send initial request
            streamRef.coroutineScope.launch {
                trace("Preparing to send initial request")
                if (requestChannel.isClosedForSend) {
                    trace(
                        message = "Request channel closed before sending initial request",
                        type = TraceType.Error,
                    )
                    if (cont.isActive) {
                        cont.resume(null as ResultType)
                    }
                    return@launch
                }
                val request = initialRequest()
                val sendResult = requestChannel.trySend(request)
                if (sendResult.isSuccess) {
                    trace("Initial request sent successfully")
                } else {
                    trace(
                        message = "Initial request send failed: $sendResult",
                        type = TraceType.Error,
                    )
                    try {
                        requestChannel.send(request)
                        trace("Initial request sent successfully (fallback send)")
                    } catch (e: Exception) {
                        trace(
                            message = "Failed to send initial request: ${e.message}",
                            type = TraceType.Error,
                        )
                        if (cont.isActive) {
                            cont.resume(null as ResultType)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // Ignore
            } else {
                trace(
                    message = "Stream setup error: ${e.message}",
                    type = TraceType.Error,
                )
                onError(e)
                if (cont.isActive) {
                    cont.resume(null as ResultType)
                }
            }
        }
    }
}