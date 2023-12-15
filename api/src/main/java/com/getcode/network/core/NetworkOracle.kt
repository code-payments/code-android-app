package com.getcode.network.core

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val INFINITE_STREAM_TIMEOUT = -1L
const val DEFAULT_STREAM_TIMEOUT = 15L

//interface NetworkOracle {
//    suspend fun <ResponseType : Any> managedRequest(
//        request: suspend () -> ResponseType
//    ): Flow<ResponseType> = managedRequest(request)
//
//    suspend fun <ResponseType : Any> managedRequest(
//        request: suspend () -> ResponseType,
//        timeout: Long = DEFAULT_STREAM_TIMEOUT
//    ): Flow<ResponseType>
//}

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

//class NetworkOracleImpl : NetworkOracle {
//    override suspend fun <ResponseType : Any> managedRequest(
//        request: suspend () -> ResponseType,
//        timeout: Long
//    ): Flow<ResponseType> = flow {
//        withContext(Dispatchers.IO) {
//            if (timeout != INFINITE_STREAM_TIMEOUT) {
//                withTimeout(timeout) {
//                    emit(request())
//                }
//            } else {
//                emit(request())
//            }
//        }
//    }
//}
