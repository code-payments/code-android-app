package com.getcode.network.core

import io.grpc.ManagedChannel
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlin.reflect.KFunction2

abstract class GrpcApi(protected val managedChannel: ManagedChannel) {
    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsSingle(
        request: Request
    ): Single<Response> = internalCallAsSingle(request)

    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsFlowable(
        request: Request,
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flowable<Response> = internalCallAsFlowable(request, backpressureStrategy)

    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsCancellableFlowable(
        request: Request,
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flowable<Response> = internalCallAsCancellableFlowable(request, backpressureStrategy)

    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsFlow(
        request: Request,
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flow<Response> = internalCallAsFlow(request, backpressureStrategy)

    fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.callAsCancellableFlow(
        request: Request,
        backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    ): Flow<Response> = internalCallAsCancellableFlow(request, backpressureStrategy)
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsSingle(
    request: Request
): Single<Response> {
    return Single.create { emitter ->
        val observer = object : ClientResponseObserver<Request, Response> {
            override fun onNext(value: Response) = emitter.onSuccess(value)
            override fun onError(error: Throwable) = emitter.onError(error)
            override fun onCompleted() {}

            override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) { }
        }

        try {
            this(request, observer)
        } catch (error: Throwable) {
            emitter.onError(error)
        }
    }
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsFlowable(
    request: Request,
    backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
): Flowable<Response> {
    return Flowable.create({ emitter ->
        val observer = object : ClientResponseObserver<Request, Response> {
            override fun onNext(value: Response) = emitter.onNext(value)
            override fun onError(error: Throwable) = emitter.onError(error)
            override fun onCompleted() = emitter.onComplete()

            override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) { }
        }

        try {
            this(request, observer)
        } catch (error: Throwable) {
            emitter.onError(error)
        }
    }, backpressureStrategy)
}

internal  fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsFlow(
    request: Request,
    backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
): Flow<Response> {
    return internalCallAsFlowable(request, backpressureStrategy).asFlow()
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsCancellableFlowable(
    request: Request,
    backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
): Flowable<Response> {
    var streamObserver: ClientCallStreamObserver<Request>? = null
    return Flowable.create<Response>({ emitter ->
        val observer = object : ClientResponseObserver<Request, Response> {
            override fun onNext(value: Response) {
                if (!emitter.isCancelled) {
                    emitter.onNext(value)
                }
            }

            override fun onError(error: Throwable) {
                if (!emitter.isCancelled) {
                    emitter.onError(error)
                }
            }

            override fun onCompleted() {
                if (!emitter.isCancelled) {
                    emitter.onComplete()
                }
            }

            override fun beforeStart(requestStream: ClientCallStreamObserver<Request>?) {
                streamObserver = requestStream
            }
        }

        try {
            this(request, observer)
        } catch (error: Throwable) {
            if (!emitter.isCancelled) {
                emitter.onError(error)
            }
        }
    }, backpressureStrategy)
        .doOnCancel {
            streamObserver?.cancel("subscription disposed, cancelling stream", null)
        }
}

internal fun <Request : Any, Response : Any> KFunction2<Request, StreamObserver<Response>, Unit>.internalCallAsCancellableFlow(
    request: Request,
    backpressureStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
): Flow<Response> {
    return internalCallAsCancellableFlowable(request, backpressureStrategy).asFlow()
}
