package com.getcode.network.core

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val INFINITE_STREAM_TIMEOUT = -1L
const val DEFAULT_STREAM_TIMEOUT = 15L

interface NetworkOracle {
    fun <ResponseType : Any> managedRequest(
        request: Single<ResponseType>
    ): Flowable<ResponseType> = managedRequest(request.toFlowable())

    fun <ResponseType : Any> managedRequest(
        request: Flowable<ResponseType>,
        timeout: Long = DEFAULT_STREAM_TIMEOUT
    ): Flowable<ResponseType>
}

class NetworkOracleImpl : NetworkOracle {
    private val scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    override fun <ResponseType : Any> managedRequest(
        request: Flowable<ResponseType>,
        timeout: Long
    ): Flowable<ResponseType> {
        return request
            .let {
                if (timeout != INFINITE_STREAM_TIMEOUT) {
                    it.timeout(timeout, TimeUnit.SECONDS)
                } else {
                    it
                }
            }
            .subscribeOn(scheduler)
    }
}
