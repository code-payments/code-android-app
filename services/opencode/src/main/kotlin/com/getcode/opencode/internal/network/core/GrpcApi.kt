package com.getcode.opencode.internal.network.core

import io.grpc.ManagedChannel
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KFunction2

abstract class GrpcApi(protected val managedChannel: ManagedChannel) {
    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsCancellableFlow(
        request: Request,
    ): Flow<Response> = internalCallAsCancellableFlow(request)
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsCancellableFlow(
    request: Request
): Flow<Response> = callbackFlow {
    val isCancelled = AtomicBoolean(false)

    val observer = object : ClientResponseObserver<Request, Response> {
        override fun beforeStart(requestStream: ClientCallStreamObserver<Request>) {
            requestStream.setOnReadyHandler {

            }
        }

        override fun onNext(value: Response) {
            if (!isCancelled.get()) {
                trySend(value).isSuccess
            }
        }

        override fun onError(t: Throwable) {
            if (!isCancelled.get()) {
                close(t)
            }
        }

        override fun onCompleted() {
            if (!isCancelled.get()) {
                close()
            }
        }
    }

    // Start the gRPC call with the observer
    this@internalCallAsCancellableFlow(request, observer)

    awaitClose {
        isCancelled.set(true)
    }
}