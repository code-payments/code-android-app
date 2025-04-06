package com.getcode.opencode.internal.network.core

import io.grpc.ManagedChannel
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlin.reflect.KFunction2

abstract class GrpcApi(protected val managedChannel: ManagedChannel) {
      fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsFlow(
        request: Request,
    ): Flow<Response> = internalCallAsFlow(request)

    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsCancellableFlow(
        request: Request,
    ): Flow<Response> = internalCallAsCancellableFlow(request)
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsFlow(
    request: Request
): Flow<Response> {
    return channelFlow {
        val observer = object : ClientResponseObserver<Request, Response> {
            override fun onNext(value: Response) {
                trySend(value)
            }
            override fun onError(error: Throwable) {
                close(error)
            }
            override fun onCompleted() {
                close()
            }

            override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) { }
        }

        try {
            this@internalCallAsFlow(request, observer)
        } catch (error: Throwable) {
            close(error)
        }
    }
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsCancellableFlow(
    request: Request
): Flow<Response> {
    var streamObserver: ClientCallStreamObserver<Request>? = null
    return channelFlow {
        val observer = object : ClientResponseObserver<Request, Response> {
            override fun onNext(value: Response) {
                trySend(value) // Non-suspending, safe to call from callback
            }

            override fun onError(error: Throwable) {
                close(error) // Closes the channel with an error
            }

            override fun onCompleted() {
                close() // Closes the channel normally
            }

            override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) {
                streamObserver = requestStream
            }
        }

        try {
            this@internalCallAsCancellableFlow(request, observer)
        } catch (error: Throwable) {
            close(error)
        }
    }.cancellable()
        .onCompletion { cause ->
            if (cause != null) { // Cancellation case
                streamObserver?.cancel("flow cancelled", cause)
            }
        }
}