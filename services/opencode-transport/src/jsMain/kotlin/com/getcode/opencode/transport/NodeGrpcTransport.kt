package com.getcode.opencode.transport

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Date

/**
 * [GrpcTransport] over `@grpc/grpc-js`, for Node. TLS via `credentials.createSsl()`; messages
 * cross the boundary as raw bytes, so grpc-js never sees a proto definition.
 */
class NodeGrpcTransport(
    host: String,
    port: Int = 443,
    private val deadlineMillis: Double = 15_000.0,
) : GrpcTransport {

    private val client = Client("$host:$port", credentials.createSsl())

    override suspend fun unary(method: String, request: ByteArray): ByteArray =
        suspendCancellableCoroutine { cont ->
            val call = client.makeUnaryRequest(
                method, ::toBuffer, ::fromBuffer, request, Metadata(), callOptions(),
            ) { error, value ->
                if (error != null) cont.resumeWithException(toStatusException(error))
                else cont.resume(value.unsafeCast<ByteArray>())
            }
            cont.invokeOnCancellation { call.cancel() }
        }

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> = callbackFlow {
        val call = client.makeBidiStreamRequest(method, ::toBuffer, ::fromBuffer, Metadata(), callOptions())
        var cancelledByUs = false
        call.on("data") { message: dynamic -> trySend(message.unsafeCast<ByteArray>()) }
        call.on("error") { error: dynamic ->
            if (cancelledByUs) close() else close(toStatusException(error))
        }
        call.on("end") { -> close() }
        launch {
            requests.collect { call.write(it) }
        }
        awaitClose {
            cancelledByUs = true
            call.cancel()
        }
    }

    override fun close() = client.close()

    private fun callOptions(): dynamic {
        val options: dynamic = js("({})")
        options.deadline = Date.now() + deadlineMillis
        return options
    }
}

private fun toStatusException(error: dynamic) =
    GrpcStatusException(code = error.code as Int, message = "gRPC ${error.code}: ${error.details}")

// Kotlin's ByteArray is an Int8Array; grpc-js wants a Node Buffer (a Uint8Array) back from
// `serialize` and hands one to `deserialize`. Both directions wrap the same memory.
private fun toBuffer(value: dynamic): dynamic {
    val bytes = value.unsafeCast<Int8Array>()
    return js("Buffer").from(bytes.buffer, bytes.byteOffset, bytes.length)
}

private fun fromBuffer(buffer: dynamic): dynamic = Int8Array(buffer.buffer, buffer.byteOffset, buffer.length)
