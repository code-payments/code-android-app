package com.getcode.oct24.internal.network.core

import io.grpc.ManagedChannel
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.reflect.KFunction2

abstract class GrpcApi(protected val managedChannel: ManagedChannel) {
    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsCancellableFlow(
        request: Request,

    ): Flow<Response> = internalCallAsCancellableFlow(request)
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsCancellableFlow(
    request: Request
): Flow<Response> = callbackFlow {
    var streamObserver: ClientCallStreamObserver<Request>? = null

    val observer = object : ClientResponseObserver<Request, Response> {
        override fun onNext(value: Response) {
            trySend(value).isSuccess // send value to the flow
        }

        override fun onError(error: Throwable) {
            close(error) // close the flow with error
        }

        override fun onCompleted() {
            close() // complete the flow
        }

        override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) {
            streamObserver = requestStream
        }
    }

    try {
        this@internalCallAsCancellableFlow(request, observer)
    } catch (error: Throwable) {
        close(error) // close the flow if an exception occurs
    }

    awaitClose {
        streamObserver?.cancel("subscription disposed, cancelling stream", null)
    }
}
