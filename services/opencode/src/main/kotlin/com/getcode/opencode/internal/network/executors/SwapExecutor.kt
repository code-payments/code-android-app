package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStreamForResult
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentSwap
import com.getcode.opencode.internal.network.extensions.toCode
import com.getcode.opencode.internal.network.extensions.toProps
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapResult
import com.getcode.opencode.model.transactions.SwapStartKind
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.diff
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

typealias OcpSwapStreamReference = BidirectionalStreamReference<SwapRequest, TransactionService.StatefulSwapResponse>


/**
 * [SwapExecutor] is responsible for orchestrating the execution of token swaps through a
 * bidirectional gRPC stream.
 *
 * It manages the lifecycle of the swap process, including:
 * 1. Establishing a stateful connection via [TransactionApi.swap].
 * 2. Handling server-provided parameters and signing required transactions.
 * 3. Managing stream state and cleanup through [OcpSwapStreamReference].
 * 4. Mapping server-side responses and errors into [SwapResult].
 *
 * @property api The [TransactionApi] used to communicate with the swap service.
 */
internal class SwapExecutor(
    private val api: TransactionApi,
) {
    private val streamReferenceMutex = Mutex()

    suspend fun execute(
        scope: CoroutineScope,
        request: SwapRequest,
    ): SwapResult = suspendCancellableCoroutine { cont ->
        trace(
            tag = "Swap",
            message = "Opening stream"
        )

        val streamReference = OcpSwapStreamReference(scope, "swap")

        streamReference.retain()

        val metadata = VerifiedSwapMetadata(
            id = request.swapId,
            fromMint = request.direction.sourceMint.address,
            toMint = request.direction.destinationMint.address,
            amount = request.amount.underlyingTokenAmount,
            fundingSource = when (request.kind) {
                is SwapStartKind.CurrencyCreator -> request.kind.fundingSource
            },
        )

        val intent = IntentSwap(request = request, metadata = metadata)

        scope.launch {
            try {
                val result = openSwapStream(streamReference, intent)
                cont.resume(result)
            } catch (e: Exception) {
                trace(
                    tag = "Swap",
                    message = "Failed to open swap stream.",
                    error = e
                )

                if (!cont.isCompleted) {
                    cont.resume(Result.failure(SubmitIntentError.Other(cause = e)))
                }
            }
        }

        cont.invokeOnCancellation {
            scope.launch {
                // Clean up streamReference on coroutine cancellation
                runCatching {
                    streamReferenceMutex.withLock {
                        streamReference.destroy()
                    }
                }.onFailure { throwable ->
                    trace(
                        tag = "Swap",
                        message = "Cancellation cleanup failed: ${throwable.message}",
                        type = TraceType.Silent
                    )
                }
            }
        }
    }

    private suspend fun openSwapStream(
        streamRef: OcpSwapStreamReference,
        intent: IntentSwap,
    ): SwapResult = openBidirectionalStreamForResult(
        streamRef = streamRef,
        apiCall = api::swap,
        initialRequest = { intent.initiate() },
        responseHandler = { response, onResult, requestChannel ->
            when (val result = response.responseCase) {
                TransactionService.StatefulSwapResponse.ResponseCase.SERVER_PARAMETERS -> {
                    handleServerParameters(
                        intent = intent,
                        onResult = onResult,
                        requestChannel = requestChannel,
                        serverParameters = response.serverParameters
                    )
                }

                TransactionService.StatefulSwapResponse.ResponseCase.SUCCESS -> {
                    streamRef.complete()
                    val result = response.success.toCode()
                    if (result == null) {
                        trace(
                            tag = "Swap",
                            message = "Success but failed to parse success code",
                        )
                        onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid success state"))))
                    } else {
                        trace(
                            tag = "Swap",
                            message = "Success: ($intent) (${response.success.code})",
                        )
                        onResult(Result.success(result))
                    }
                }

                TransactionService.StatefulSwapResponse.ResponseCase.ERROR -> {
                    val errors = handleErrors(intent, response.error.errorDetailsList)
                    trace(
                        tag = "Swap",
                        message = "Error: ($intent) (${response.error.code}) ${errors.joinToString("\n")}",
                        type = TraceType.Error
                    )
                    streamRef.complete()
                    onResult(Result.failure(SwapError.typed(response.error)))
                }

                TransactionService.StatefulSwapResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
            }
        }
    )
}

private fun handleServerParameters(
    intent: IntentSwap,
    serverParameters: TransactionService.StatefulSwapResponse.ServerParameters?,
    requestChannel: (TransactionService.StatefulSwapRequest) -> Unit,
    onResult: (SwapResult) -> Unit,
) {
    try {
        val params = when (serverParameters?.kindCase) {
            null -> null
            TransactionService.StatefulSwapResponse.ServerParameters.KindCase.CURRENCY_CREATOR -> {
                serverParameters.currencyCreator.toProps()
            }

            TransactionService.StatefulSwapResponse.ServerParameters.KindCase.KIND_NOT_SET -> null
        }

        if (params != null) {
            trace(
                tag = "Swap",
                message = "Received ${params.javaClass.simpleName} parameters. Submitting signatures...",
                type = TraceType.Silent
            )

            intent.parameters = params

            requestChannel(intent.requestToSubmitSignatures())
        } else {
            onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid server parameters"))))
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            e.printStackTrace()
        }
        onResult(Result.failure(SwapError.Other(cause = e)))
    }
}

private fun handleErrors(
    intent: IntentSwap,
    errorDetails: List<TransactionService.ErrorDetails>
): List<String> {
    val errors = mutableListOf<String>()

    errorDetails.forEach { error ->
        when (error.typeCase) {
            TransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                errors.add("Reason: ${error.reasonString.reason}")
            }

            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                val expected = SolanaTransaction.fromBytes(
                    error.invalidSignature.expectedTransaction.value
                )
                val produced = intent.transaction(intent.parameters!!)

                expected?.diff(produced)

                errors.addAll(
                    listOf(
                        "Action index: ${error.invalidSignature.actionId}",
                        "Invalid signature: ${
                            Signature(error.invalidSignature.providedSignature.value).base58()
                        }",
                        "Transaction bytes: ${error.invalidSignature.expectedTransaction.value}",
                        "Transaction expected: $expected",
                        "Android produced: $produced"
                    )
                )
            }

            TransactionService.ErrorDetails.TypeCase.DENIED -> {
                errors.add("Denied: ${error.denied.reason}")
            }

            else -> Unit
        }
    }

    return errors
}