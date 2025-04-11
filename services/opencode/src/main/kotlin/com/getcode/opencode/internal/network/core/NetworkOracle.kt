package com.getcode.opencode.internal.network.core

import com.getcode.utils.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

const val INFINITE_STREAM_TIMEOUT = -1L
const val DEFAULT_STREAM_TIMEOUT = 15L

interface NetworkOracle {
    fun <ResponseType : Any> managedRequest(
        request: Flow<ResponseType>,
        timeout: Long = DEFAULT_STREAM_TIMEOUT
    ): Flow<ResponseType>
}

class NetworkOracleImpl : NetworkOracle {
    private val scheduler = Dispatchers.IO // Better for concurrency

    override fun <ResponseType : Any> managedRequest(
        request: Flow<ResponseType>,
        timeout: Long
    ): Flow<ResponseType> {
        return flow {
            try {
                if (timeout != INFINITE_STREAM_TIMEOUT) {
                    withTimeout(timeout.seconds.inWholeMilliseconds) {
                        request.collect { emit(it) }
                    }
                } else {
                    request.collect { emit(it) }
                }
            } catch (e: TimeoutCancellationException) {
                trace("Timeout after $timeout seconds")
                throw NetworkTimeoutException("Request timed out after $timeout seconds")
            }
        }.flowOn(scheduler)
    }
}

class NetworkTimeoutException(message: String) : Exception(message)
