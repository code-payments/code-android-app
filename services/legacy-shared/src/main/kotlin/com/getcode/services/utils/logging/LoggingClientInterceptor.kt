package com.getcode.services.utils.logging

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import java.util.concurrent.LinkedBlockingDeque

class LoggingClientInterceptor: ClientInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>?,
        callOptions: CallOptions?,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        return LoggingClientCall(next.newCall(method, callOptions), LinkedBlockingDeque(), LinkedBlockingDeque())
    }

}