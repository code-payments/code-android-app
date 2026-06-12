package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStreamForResult
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentStatelessSwap
import com.getcode.opencode.internal.network.extensions.toCode
import com.getcode.opencode.internal.network.extensions.toProps
import com.getcode.opencode.internal.network.extensions.toSignature
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.StatelessSwapRequest
import com.getcode.opencode.model.transactions.StatelessSwapResult
import com.getcode.opencode.model.transactions.StatelessSwapSuccess
import com.getcode.services.opencode.BuildConfig
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

private typealias StatelessSwapStreamReference =
    BidirectionalStreamReference<OcpTransactionService.StatelessSwapRequest, OcpTransactionService.StatelessSwapResponse>

internal class StatelessSwapExecutor(
    private val api: TransactionApi,
) {
    private val streamReferenceMutex = Mutex()

    suspend fun execute(
        scope: CoroutineScope,
        request: StatelessSwapRequest,
    ): StatelessSwapResult = suspendCancellableCoroutine { cont ->
        trace(tag = TAG, message = "Opening stateless swap stream")

        val streamReference = StatelessSwapStreamReference(scope, "stateless-swap")
        streamReference.retain()

        val intent = IntentStatelessSwap(request = request)

        scope.launch {
            try {
                val result = openStream(streamReference, intent)
                cont.resume(result)
            } catch (e: Exception) {
                trace(tag = TAG, message = "Failed to open stateless swap stream.", error = e)
                if (!cont.isCompleted) {
                    cont.resume(Result.failure(SubmitIntentError.Other(cause = e)))
                }
            }
        }

        cont.invokeOnCancellation {
            scope.launch {
                runCatching {
                    streamReferenceMutex.withLock { streamReference.destroy() }
                }.onFailure { throwable ->
                    trace(
                        tag = TAG,
                        message = "Cancellation cleanup failed: ${throwable.message}",
                        type = TraceType.Silent
                    )
                }
            }
        }
    }

    private suspend fun openStream(
        streamRef: StatelessSwapStreamReference,
        intent: IntentStatelessSwap,
    ): StatelessSwapResult = openBidirectionalStreamForResult(
        streamRef = streamRef,
        apiCall = api::statelessSwap,
        initialRequest = { intent.initiate() },
        responseHandler = { response, onResult, requestChannel ->
            when (response.responseCase) {
                OcpTransactionService.StatelessSwapResponse.ResponseCase.SERVER_PARAMETERS -> {
                    handleServerParameters(
                        intent = intent,
                        serverParameters = response.serverParameters,
                        onResult = onResult,
                        requestChannel = requestChannel,
                    )
                }

                OcpTransactionService.StatelessSwapResponse.ResponseCase.SUCCESS -> {
                    streamRef.complete()
                    val code = response.success.toCode()
                    val signature = runCatching { response.success.transactionSignature.toSignature() }.getOrNull()
                    if (code == null || signature == null) {
                        trace(tag = TAG, message = "Success but failed to parse response")
                        onResult(Result.failure(SwapError.Other()))
                    } else {
                        trace(tag = TAG, message = "Success: ${response.success.code}")
                        onResult(Result.success(StatelessSwapSuccess(code, signature)))
                    }
                }

                OcpTransactionService.StatelessSwapResponse.ResponseCase.ERROR -> {
                    val errors = handleErrors(response.error.errorDetailsList)
                    trace(
                        tag = TAG,
                        message = "Error: (${response.error.code}) ${errors.joinToString("\n")}",
                        type = TraceType.Error,
                    )
                    streamRef.complete()
                    onResult(Result.failure(SwapError.typed(response.error)))
                }

                OcpTransactionService.StatelessSwapResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
            }
        }
    )
}

private fun handleServerParameters(
    intent: IntentStatelessSwap,
    serverParameters: OcpTransactionService.StatelessSwapResponse.ServerParameters?,
    requestChannel: (OcpTransactionService.StatelessSwapRequest) -> Unit,
    onResult: (StatelessSwapResult) -> Unit,
) {
    try {
        val params = when (serverParameters?.kindCase) {
            OcpTransactionService.StatelessSwapResponse.ServerParameters.KindCase.STABLECOIN ->
                serverParameters.stablecoin.toProps()
            OcpTransactionService.StatelessSwapResponse.ServerParameters.KindCase.KIND_NOT_SET,
            null -> null
        }

        if (params == null) {
            onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid server parameters"))))
            return
        }

        trace(tag = TAG, message = "Received server parameters. Building transaction and submitting signatures...")

        intent.parameters = params
        requestChannel(intent.requestToSubmitSignatures())
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) e.printStackTrace()
        onResult(Result.failure(SwapError.Other(cause = e)))
    }
}

private fun handleErrors(
    errorDetails: List<OcpTransactionService.ErrorDetails>
): List<String> {
    val errors = mutableListOf<String>()
    errorDetails.forEach { error ->
        when (error.typeCase) {
            OcpTransactionService.ErrorDetails.TypeCase.REASON_STRING ->
                errors.add("Reason: ${error.reasonString.reason}")
            OcpTransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE ->
                errors.add("Action index: ${error.invalidSignature.actionId}")
            OcpTransactionService.ErrorDetails.TypeCase.DENIED ->
                errors.add("Denied: ${error.denied.reason}")
            else -> Unit
        }
    }
    return errors
}

private const val TAG = "StatelessSwap"
