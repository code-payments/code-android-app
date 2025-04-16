package com.getcode.services.utils.logging

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.ClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Status
import java.util.Queue

class LoggingClientCallListener<ReqT, ResT>(
    delegate: ClientCall.Listener<ResT>,
    private val requestQueue: Queue<ReqT>,
    private val responseQueue: Queue<ResT>
) : ForwardingClientCallListener.SimpleForwardingClientCallListener<ResT>(delegate) {

    override fun onMessage(message: ResT) {
        responseQueue.offer(message)
        super.onMessage(message)
    }

    override fun onClose(status: Status, trailers: io.grpc.Metadata?) {
        val requestLog = requestQueue.joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]"
        ) { getObjectDetails(it) }
        val responseLog = responseQueue.joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]"
        ) { getObjectDetails(it) }

        if (status.isOk) {
            trace(tag = "RpcLogging", message = "Request: $requestLog", type = TraceType.Network)
            trace(tag = "RpcLogging", message = "Response: $responseLog", type = TraceType.Network)
            trace(
                tag = "RpcLogging",
                message = "The request was processed successfully",
                type = TraceType.Network
            )
        } else if (UNSUCCESSFUL_STATUS_CODES.contains(status.code)) {
            trace(tag = "RpcLogging", message = "Request: $requestLog", type = TraceType.Network)
            trace(
                tag = "RpcLogging",
                message = "An error occurred while processing the request",
                type = TraceType.Error,
                error = status.asRuntimeException()
            )
        }

        super.onClose(status, trailers)
    }

    private fun getObjectDetails(obj: Any?, maxDepth: Int = 2, currentDepth: Int = 0): String {
        if (obj == null) return "null"

        // Prevents infinitely deep recursion by setting a maximum depth
        if (currentDepth >= maxDepth) return "${obj::class.java.simpleName}(...)"

        return try {
            val fields = obj::class.java.declaredFields
            fields.joinToString(", ", "${obj::class.java.simpleName}(", ")") { field ->
                field.isAccessible = true
                val value = field.get(obj)

                // Check if the field itself is another complex object (not a primitive or string), then call recursively
                val valueString = when {
                    value == null -> "null"
                    value::class.java.isPrimitive || value is String -> value.toString()
                    else -> getObjectDetails(value, maxDepth, currentDepth + 1)
                }

                "${field.name}=$valueString"
            }
        } catch (e: Exception) {
            "Unable to log details for ${obj::class.java.simpleName}"
        }
    }


    companion object {
        private val UNSUCCESSFUL_STATUS_CODES = listOf(
            Status.INVALID_ARGUMENT.code,
            Status.INTERNAL.code,
            Status.NOT_FOUND.code
        )
    }
}