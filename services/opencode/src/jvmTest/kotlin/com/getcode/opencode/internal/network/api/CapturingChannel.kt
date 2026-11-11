package com.getcode.opencode.internal.network.api

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit

/** Records each message's serialized bytes and closes the call with UNAVAILABLE. */
internal class CapturingChannel : ManagedChannel() {
    private val captured = mutableListOf<ByteArray>()

    fun takeOnly(): ByteArray = captured.single().also { captured.clear() }

    override fun <Req, Resp> newCall(method: MethodDescriptor<Req, Resp>, options: CallOptions): ClientCall<Req, Resp> =
        object : ClientCall<Req, Resp>() {
            private var listener: Listener<Resp>? = null
            override fun start(responseListener: Listener<Resp>, headers: Metadata) { listener = responseListener }
            override fun request(numMessages: Int) = Unit
            override fun cancel(message: String?, cause: Throwable?) = Unit
            override fun sendMessage(message: Req) {
                captured += method.streamRequest(message).readBytes()
            }
            override fun halfClose() {
                listener?.onClose(Status.UNAVAILABLE.withDescription("captured, not sent"), Metadata())
            }
        }

    override fun authority(): String = "capture.invalid"
    override fun shutdown(): ManagedChannel = this
    override fun isShutdown(): Boolean = false
    override fun isTerminated(): Boolean = false
    override fun shutdownNow(): ManagedChannel = this
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
