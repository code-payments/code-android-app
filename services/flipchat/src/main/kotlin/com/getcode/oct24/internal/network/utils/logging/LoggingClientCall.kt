package com.getcode.oct24.internal.network.utils.logging

import io.grpc.ClientCall
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import java.util.Queue

internal class LoggingClientCall<ReqT, ResT>(
    delegate: ClientCall<ReqT, ResT>,
    private val requestQueue: Queue<ReqT>,
    private val responseQueue: Queue<ResT>,
): SimpleForwardingClientCall<ReqT, ResT>(delegate) {

    override fun sendMessage(message: ReqT) {
        requestQueue.offer(message)
        super.sendMessage(message)
    }

    override fun start(responseListener: Listener<ResT>, headers: io.grpc.Metadata) {
        super.start(LoggingClientCallListener(responseListener, requestQueue, responseQueue), headers)
    }
}