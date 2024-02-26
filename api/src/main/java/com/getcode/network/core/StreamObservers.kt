package com.getcode.network.core

import io.grpc.stub.StreamObserver
import timber.log.Timber


class BidirectionalStreamReference<Request, Response> : AutoCloseable {

    var stream: StreamObserver<Request>? = null

    private var closure: (() -> BidirectionalStreamReference<Request, Response>)? = null

    override fun close() {
        Timber.d("Deallocating bidirectional stream reference:")
    }

    fun cancel() {
        stream?.onCompleted()
    }

    fun retain() {
        closure = { this }
    }

    fun release() {
        closure = null
    }
}