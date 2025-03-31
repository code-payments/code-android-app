package xyz.flipchat.services.internal.network.service

import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

suspend fun <ResponseType: Any, Output: Any> NetworkOracle.managedApiRequest(
    call: () -> Flow<ResponseType>,
    handleResponse: (ResponseType) -> Result<Output>,
    onOtherError: (Exception) -> Result<Output>
): Result<Output> {
    return try {
        managedRequest(call())
            .map { response -> handleResponse(response) }.first()
    } catch (e: Exception) {
        onOtherError(e)
    }
}