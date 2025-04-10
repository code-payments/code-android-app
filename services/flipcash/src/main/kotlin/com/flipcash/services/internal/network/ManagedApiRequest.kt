package com.flipcash.services.internal.network

import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

suspend fun <ResponseType: Any, Output: Any> NetworkOracle.managedApiRequest(
    call: () -> Flow<ResponseType>,
    timeout: Long = DEFAULT_STREAM_TIMEOUT,
    handleResponse: (ResponseType) -> Result<Output>,
    onOtherError: (Exception) -> Result<Output>
): Result<Output> {
    return try {
        managedRequest(call(), timeout)
            .map { response -> handleResponse(response) }.first()
    } catch (e: Exception) {
        onOtherError(e)
    }
}