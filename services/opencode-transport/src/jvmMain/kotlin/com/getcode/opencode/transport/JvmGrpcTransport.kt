package com.getcode.opencode.transport

import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.StatusException
import io.grpc.kotlin.ClientCalls
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * [GrpcTransport] over grpc-java (OkHttp, TLS) for the JVM. Messages cross as bytes through a
 * pass-through marshaller, so no protobuf runtime is on this path.
 */
class JvmGrpcTransport(
    host: String,
    port: Int = 443,
) : GrpcTransport {

    private val channel: ManagedChannel = OkHttpChannelBuilder.forAddress(host, port)
        .useTransportSecurity()
        .build()

    override suspend fun unary(method: String, request: ByteArray): ByteArray = try {
        ClientCalls.unaryRpc(channel, descriptor(method, MethodDescriptor.MethodType.UNARY), request, CallOptions.DEFAULT)
    } catch (e: StatusException) {
        throw e.toStatusException()
    }

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> =
        ClientCalls.bidiStreamingRpc(
            channel,
            descriptor(method, MethodDescriptor.MethodType.BIDI_STREAMING),
            requests,
            CallOptions.DEFAULT,
        ).catch { e -> throw if (e is StatusException) e.toStatusException() else e }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun descriptor(method: String, type: MethodDescriptor.MethodType): MethodDescriptor<ByteArray, ByteArray> =
        MethodDescriptor.newBuilder(BytesMarshaller, BytesMarshaller)
            .setType(type)
            .setFullMethodName(method.removePrefix("/"))
            .build()

    private fun StatusException.toStatusException() =
        GrpcStatusException(status.code.value(), "${status.code}: ${status.description.orEmpty()}")

    private object BytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
        override fun parse(stream: InputStream): ByteArray = stream.readBytes()
    }
}
