package com.getcode.opencode.utils.logging

import com.getcode.utils.TraceManager
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
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
        if (TraceManager.includeRpcBodies) {
            val requestLog = requestQueue.joinToString(separator = ", ", prefix = "[", postfix = "]") { formatProto(it) }
            val responseLog = responseQueue.joinToString(separator = ", ", prefix = "[", postfix = "]") { formatProto(it) }

            trace(tag = "RpcLogging", message = "Request: $requestLog", type = TraceType.Network)
            if (status.isOk) {
                trace(tag = "RpcLogging", message = "Response: $responseLog", type = TraceType.Network)
            }
        }

        when {
            status.isOk -> {
                trace(
                    tag = "RpcLogging",
                    message = "The request was processed successfully",
                    type = TraceType.Network
                )
            }
            status.code == Status.Code.CANCELLED -> {
                trace(
                    tag = "RpcLogging",
                    message = "Request cancelled",
                    type = TraceType.Log,
                )
            }
            else -> {
                trace(
                    tag = "RpcLogging",
                    message = "An error occurred while processing the request",
                    type = TraceType.Error,
                    error = status.asRuntimeException(trailers)
                )
            }
        }

        super.onClose(status, trailers)
    }
}

/**
 * Proto simple names whose single `bytes value` field should be rendered as base58.
 */
private val BASE58_WRAPPER_NAMES = setOf(
    "SolanaAccountId", "PublicKey", "Signature",
    "Hash", "Blockhash", "IntentId", "SwapId",
)

/**
 * Formats a gRPC message (expected to be a protobuf [MessageLite]) into a
 * human-readable single-line string.  Known byte-wrapper types are rendered
 * as base58; UUIDs are rendered in standard 8-4-4-4-12 form; all other
 * messages have their fields printed recursively.
 */
private fun formatProto(obj: Any?): String {
    if (obj == null) return "null"
    if (obj !is MessageLite) return obj.toString()

    return try {
        val name = obj::class.java.simpleName

        // Byte-wrapper → base58
        if (name in BASE58_WRAPPER_NAMES) {
            val bytes = obj.getByteField("value_") ?: return name
            return Base58.encode(bytes)
        }

        // UUID wrapper → standard format
        if (name == "UUID") {
            val bytes = obj.getByteField("value_") ?: return name
            return formatUuid(bytes)
        }

        // General message: iterate declared fields via reflection on the
        // generated lite class. We look for non-default fields and format
        // each value according to its type.
        val parts = mutableListOf<String>()
        for (field in obj::class.java.declaredFields) {
            // Skip static / synthetic / internal protobuf bookkeeping fields.
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            val fieldName = field.name
            if (fieldName.startsWith("PARSER") ||
                fieldName == "DEFAULT_INSTANCE" ||
                fieldName == "DEFAULT_INSTANCE_DATA" ||
                fieldName.endsWith("FIELD_NUMBER") ||
                fieldName.startsWith("memoizedSerializedSize") ||
                fieldName.startsWith("memoizedHash") ||
                fieldName.startsWith("unknownFields")
            ) continue

            field.isAccessible = true
            val value = field.get(obj) ?: continue

            val formatted = formatFieldValue(fieldName, value)
            if (formatted != null) {
                // Strip the trailing underscore that protobuf-lite adds.
                val displayName = fieldName.trimEnd('_')
                parts += "$displayName=$formatted"
            }
        }
        if (parts.isEmpty()) return name
        "$name { ${parts.joinToString(", ")} }"
    } catch (_: Exception) {
        "Unable to format ${obj::class.java.simpleName}"
    }
}

private fun formatFieldValue(fieldName: String, value: Any): String? = when (value) {
    is MessageLite -> {
        // Skip default (empty) sub-messages.
        if (value.serializedSize == 0) null else formatProto(value)
    }
    is ByteString -> {
        if (value.isEmpty) null else "<${value.size()} bytes>"
    }
    is List<*> -> {
        if (value.isEmpty()) null
        else "[${value.joinToString(", ") { formatFieldValue(fieldName, it ?: "null") ?: "null" }}]"
    }
    is Map<*, *> -> {
        if (value.isEmpty()) null
        else "{${value.entries.joinToString(", ") { (k, v) ->
            "${formatFieldValue(fieldName, k ?: "null") ?: k} -> ${formatFieldValue(fieldName, v ?: "null") ?: v}"
        }}}"
    }
    is Int -> if (value == 0) null else value.toString()
    is Long -> if (value == 0L) null else value.toString()
    is Boolean -> if (!value) null else "true"
    is Float -> if (value == 0f) null else value.toString()
    is Double -> if (value == 0.0) null else value.toString()
    is String -> if (value.isEmpty()) null else "\"${value.masked()}\""
    is Enum<*> -> {
        // Proto enums: value 0 is the default/unset sentinel.
        if (value.ordinal == 0) null else value.name
    }
    else -> value.toString().masked()
}

/** Read a [ByteString] instance field by name via reflection. */
private fun MessageLite.getByteField(name: String): ByteArray? {
    return try {
        val f = this::class.java.getDeclaredField(name)
        f.isAccessible = true
        (f.get(this) as? ByteString)?.toByteArray()
    } catch (_: Exception) {
        null
    }
}

private fun formatUuid(bytes: ByteArray): String {
    if (bytes.size != 16) return bytes.toHex()
    return buildString {
        append(bytes.toHex(0, 4))
        append('-')
        append(bytes.toHex(4, 6))
        append('-')
        append(bytes.toHex(6, 8))
        append('-')
        append(bytes.toHex(8, 10))
        append('-')
        append(bytes.toHex(10, 16))
    }
}

private fun ByteArray.toHex(from: Int = 0, to: Int = size): String =
    (from until to).joinToString("") { "%02x".format(this[it]) }

/** Apply all registered [TraceManager] plugins (e.g. PII masking) to a string. */
private fun String.masked(): String =
    TraceManager.plugins.fold(this) { acc, plugin -> plugin.process(acc) ?: acc }