package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SubmitIntentRequest
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SubmitIntentResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStreamForResult
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.diff
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.base58
import com.getcode.solana.keys.redact
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeoutException
import kotlin.collections.forEach
import kotlin.coroutines.resume

typealias OcpIntentStreamReference = BidirectionalStreamReference<SubmitIntentRequest, SubmitIntentResponse>

class IntentExecutor(
    private val api: TransactionApi,
) {
    private val streamReferenceMutex = Mutex()

    suspend fun execute(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = suspendCancellableCoroutine { cont ->
        trace(
            tag = "SubmitIntent",
            message = "Opening stream."
        )
        val streamReference = OcpIntentStreamReference(scope, "submitIntent")

        streamReference.timeoutHandler = {
            trace(
                tag = "SubmitIntent",
                message = "Stream timed out, cleaning up.",
                type = TraceType.Error
            )
            streamReference.cancel()
            if (!cont.isCompleted) {
                cont.resume(Result.failure(SubmitIntentError.Other(
                    cause = TimeoutException("Intent stream timed out")
                )))
            }
        }

        streamReference.retain()

        scope.launch {
            try {
                val result = openIntentStream(streamReference, intent, owner)
                cont.resume(result)
            } catch (e: Exception) {
                trace(
                    tag = "SubmitIntent",
                    message = "Failed to open intent stream.",
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
                        tag = "SubmitIntent",
                        message = "Cancellation cleanup failed: ${throwable.message}",
                        type = TraceType.Silent
                    )
                }
            }
        }
    }

    private suspend fun openIntentStream(
        streamRef: OcpIntentStreamReference,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = openBidirectionalStreamForResult(
        streamRef = streamRef,
        apiCall = { request ->
            trace(
                tag = "SubmitIntent",
                message = "submitting intent ${intent.id.base58().redact()}"
            )
            api.submitIntent(request)
        },
        initialRequest = { intent.requestToSubmitActions(owner) },
        responseHandler = { response, onResult, requestChannel ->
            when (val result = response.responseCase) {
                SubmitIntentResponse.ResponseCase.SERVER_PARAMETERS -> {
                    handleServerParameters(
                        intent = intent,
                        onResult = onResult,
                        requestChannel = requestChannel,
                        serverParameters = response.serverParameters.serverParametersList
                    )
                }

                SubmitIntentResponse.ResponseCase.SUCCESS -> {
                    streamRef.complete()
                    onResult(Result.success(intent))
                }

                SubmitIntentResponse.ResponseCase.ERROR -> {
                    val errors = handleErrors(
                        intent,
                        response.error.errorDetailsList
                    )
                    trace(
                        tag = "SubmitIntent",
                        message = "Error: ($intent) ${errors.joinToString("\n")}",
                        type = TraceType.Error
                    )
                    streamRef.complete()
                    onResult(Result.failure(SubmitIntentError.typed(response.error)))
                }

                SubmitIntentResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
            }
        }
    )

    private fun handleServerParameters(
        intent: IntentType,
        serverParameters: List<OcpTransactionService.ServerParameter>,
        requestChannel: (SubmitIntentRequest) -> Unit,
        onResult: (Result<IntentType>) -> Unit,
    ) {
        try {
            intent.apply(serverParameters.map { p -> ServerParameter.newInstance(p) })

            trace(
                tag = "SubmitIntent",
                message = "Received ${serverParameters.size} parameters. Submitting signatures...",
                type = TraceType.Silent
            )

            val submitSignatures = intent.requestToSubmitSignatures()
            requestChannel(submitSignatures)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            trace(
                tag = "SubmitIntent",
                message = "Received ${serverParameters.size} parameters but failed to apply them: ${e.javaClass.simpleName} ${e.message})",
                type = TraceType.Silent
            )
            onResult(Result.failure(SubmitIntentError.Other(cause = e)))
        }
    }

    private fun handleErrors(
        intent: IntentType,
        errorDetails: List<OcpTransactionService.ErrorDetails>
    ): List<String> {
        val errors = mutableListOf<String>()

        errorDetails.forEach { error ->
            when (error.typeCase) {
                OcpTransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                    errors.add("Reason: ${error.reasonString.reason}")
                }

                OcpTransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                    val expected = SolanaTransaction.fromList(error.invalidSignature.expectedTransaction.value.toByteArray().toList())
                    val produced = intent.transaction()
                    errors.addAll(
                        listOf(
                            "Action index: ${error.invalidSignature.actionId}",
                            "Invalid signature: ${
                                com.getcode.solana.keys.Signature(
                                    error.invalidSignature.providedSignature.value.toByteArray()
                                        .toList()
                                ).base58()}",
                            "Transaction bytes: ${error.invalidSignature.expectedTransaction.value}",
                            "Transaction expected: $expected",
                        )
                    )

                    expected?.diff(produced)
                }

                OcpTransactionService.ErrorDetails.TypeCase.DENIED -> {
                    errors.add("Denied: ${error.denied.reason}")
                }

                else -> Unit
            }
        }

        return errors
    }
}