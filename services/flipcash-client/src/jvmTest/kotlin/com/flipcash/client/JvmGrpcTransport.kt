package com.flipcash.client

import com.getcode.opencode.transport.GrpcStatusException
import com.getcode.opencode.transport.GrpcTransport
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.StreamObserver
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [GrpcTransport] over grpc-java (OkHttp, TLS), for the JVM live test only. Messages cross as raw
 * bytes, as in NodeGrpcTransport. Not a production transport: it lives in jvmTest until the
 * transport module gets a JVM actual.
 */
internal class JvmGrpcTransport(host: String, port: Int = 443) : GrpcTransport {
    private val grpcChannel: ManagedChannel = OkHttpChannelBuilder.forAddress(host, port).useTransportSecurity().build()

    override suspend fun unary(method: String, request: ByteArray): ByteArray = suspendCancellableCoroutine { cont ->
        val call = grpcChannel.newCall(descriptor(method, MethodDescriptor.MethodType.UNARY), CallOptions.DEFAULT.withDeadlineAfter(15, TimeUnit.SECONDS))
        ClientCalls.asyncUnaryCall(call, request, object : StreamObserver<ByteArray> {
            override fun onNext(value: ByteArray) { cont.resume(value) }
            override fun onError(t: Throwable) { cont.resumeWithException(t.toStatusException()) }
            override fun onCompleted() = Unit
        })
        cont.invokeOnCancellation { call.cancel("cancelled", null) }
    }

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> = callbackFlow {
        val call = grpcChannel.newCall(descriptor(method, MethodDescriptor.MethodType.BIDI_STREAMING), CallOptions.DEFAULT)
        val outgoing = ClientCalls.asyncBidiStreamingCall(call, object : StreamObserver<ByteArray> {
            override fun onNext(value: ByteArray) { trySend(value) }
            override fun onError(t: Throwable) { close(t.toStatusException()) }
            override fun onCompleted() { close() }
        })
        val writer = launch {
            requests.collect { outgoing.onNext(it) }
            outgoing.onCompleted()
        }
        awaitClose {
            writer.cancel()
            call.cancel("closed by client", null)
        }
    }

    override fun close() {
        grpcChannel.shutdownNow()
    }

    private fun descriptor(method: String, type: MethodDescriptor.MethodType) =
        MethodDescriptor.newBuilder(BytesMarshaller, BytesMarshaller)
            .setFullMethodName(method.removePrefix("/"))
            .setType(type)
            .build()

    private object BytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
        override fun parse(stream: InputStream): ByteArray = stream.readBytes()
    }
}

private fun Throwable.toStatusException(): Throwable = when (this) {
    is StatusRuntimeException -> GrpcStatusException(status.code.value(), status.toString())
    else -> Status.fromThrowable(this).let { GrpcStatusException(it.code.value(), it.toString()) }
}
