package com.flipcash.services.internal.network

import android.util.Log
import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

suspend fun <ResponseType: Any, Output: Any> NetworkOracle.managedApiRequest(
    call: () -> Flow<ResponseType>,
    timeout: Long = DEFAULT_STREAM_TIMEOUT,
    handleResponse: (ResponseType) -> Result<Output>,
    onOtherError: (Exception) -> Result<Output>
): Result<Output> {
    Log.d("ManagedApiRequest", "Starting with timeout: ${timeout.seconds.inWholeMilliseconds}")
    return try {
        val result = managedRequest(call(), timeout)
            .map { response -> handleResponse(response) }
            .onEach { Log.d("ManagedApiRequest", "Emitted: $it") }
            .firstOrNull()
        result ?: onOtherError(IllegalStateException("No response received from server"))
    } catch (e: Exception) {
        Log.e("ManagedApiRequest", "Error: $e")
        onOtherError(e)
    }
}