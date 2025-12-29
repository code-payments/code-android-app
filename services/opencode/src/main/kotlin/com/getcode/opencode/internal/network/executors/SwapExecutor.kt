package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentSwap
import com.getcode.opencode.internal.network.extensions.toCode
import com.getcode.opencode.internal.network.extensions.toStatefulProps
import com.getcode.opencode.internal.network.extensions.toStatelessProps
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapResult
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.diff
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias OcpSwapStreamReference = BidirectionalStreamReference<SwapRequest, TransactionService.SwapResponse>


internal class SwapExecutor @Inject constructor(
    private val api: TransactionApi,
    private val swapService: SwapService,
) {
    suspend fun execute(
        scope: CoroutineScope,
        request: SwapRequest,
    ): SwapResult {
        val metadata = runCatching {
            pollSwapUntilFunded(
                swapId = request.swapId,
                owner = request.owner,
                maxAttempts = 30,
                interval = 1.seconds
            )
        }.getOrElse { error ->
            return SwapResult.failure(error)
        }

        val intent = IntentSwap(
            id = request.swapId,
            owner = request.owner,
            verifiedSwapMetadata = metadata.verifiedMetadata,
            swapAuthority = request.swapAuthority,
            amountInQuarks = metadata.amount.quarks,
            direction = request.direction,
            waitForBlockchain = true,
        )

        return executeInternal(
            scope = scope,
            intent = intent,
        )
    }

    private suspend fun pollSwapUntilFunded(
        swapId: SwapId,
        owner: AccountCluster,
        maxAttempts: Int,
        interval: Duration,
        attempt: Int = 0
    ): SwapMetadata {
        if (attempt >= maxAttempts) {
            trace(
                type = TraceType.Error,
                message = "Polling timed out after $maxAttempts attempts, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Other()
        }

        val metadata = try {
            swapService.getSwap(swapId, owner.authority.keyPair).getOrThrow()
        } catch (e: Exception) {
            trace(
                type = TraceType.Error,
                message = "Failed to get swap state: $e, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Other(cause = e)
        }

        return when (metadata.state) {
            SwapState.FUNDED,
            SwapState.FINALIZED -> {
                // Swap is ready to execute or already finalized
                metadata
            }

            SwapState.FAILED,
            SwapState.CANCELLED -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap reached terminal state: ${metadata.state}, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Other()
            }

            SwapState.CREATED,
            SwapState.FUNDING,
            SwapState.SUBMITTING,
            SwapState.CANCELLING -> {
                // Still in progress, poll again
                trace(
                    type = TraceType.Log,
                    message = "Swap state: ${metadata.state}, polling again..., Attempt ${attempt + 1}/$maxAttempts"
                )
                delay(interval)
                pollSwapUntilFunded(swapId, owner, maxAttempts, interval, attempt + 1)
            }

            SwapState.UNKNOWN -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap in unknown state, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Other()
            }
        }
    }

    private suspend fun executeInternal(
        scope: CoroutineScope,
        intent: IntentSwap,
    ): SwapResult = suspendCancellableCoroutine { cont ->
        trace(
            tag = "Swap",
            message = "Opening stream"
        )

        val streamReference = OcpSwapStreamReference(scope, "swap")

        streamReference.retain()

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

    }

    private suspend fun openSwapStream(
        streamRef: OcpSwapStreamReference,
        intent: IntentSwap,
    ): SwapResult = openBidirectionalStream(
        streamRef = streamRef,
        apiCall = api::swap,
        initialRequest = { intent.initiate() },
        responseHandler = { response, onResult, requestChannel ->
            when (val result = response.responseCase) {
                TransactionService.SwapResponse.ResponseCase.SERVER_PARAMETERS -> {
                    handleServerParameters(
                        intent = intent,
                        onResult = onResult,
                        requestChannel = requestChannel,
                        serverParameters = response.serverParameters
                    )
                }

                TransactionService.SwapResponse.ResponseCase.SUCCESS -> {
                    streamRef.complete()
                    val result = response.success.toCode()
                    if (result == null) {
                        onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid success state"))))
                    } else {
                        onResult(Result.success(result))
                    }
                }

                TransactionService.SwapResponse.ResponseCase.ERROR -> {
                    val errors = handleErrors(intent, response.error.errorDetailsList)
                    trace(
                        tag = "Swap",
                        message = "Error: ($intent) ${errors.joinToString("\n")}",
                        type = TraceType.Error
                    )
                    streamRef.complete()
                    onResult(Result.failure(SwapError.typed(response.error)))
                }

                TransactionService.SwapResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
            }
        }
    )
}

private fun handleServerParameters(
    intent: IntentSwap,
    serverParameters: TransactionService.SwapResponse.ServerParameters?,
    requestChannel: (TransactionService.SwapRequest) -> Unit,
    onResult: (SwapResult) -> Unit,
) {
    try {
        val params = when (serverParameters?.kindCase) {
            null -> null
            TransactionService.SwapResponse.ServerParameters.KindCase.CURRENCY_CREATOR_STATELESS -> {
                serverParameters.currencyCreatorStateless.toStatelessProps()
            }

            TransactionService.SwapResponse.ServerParameters.KindCase.CURRENCY_CREATOR_STATEFUL -> {
                serverParameters.currencyCreatorStateful.toStatefulProps()
            }

            TransactionService.SwapResponse.ServerParameters.KindCase.KIND_NOT_SET -> null
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