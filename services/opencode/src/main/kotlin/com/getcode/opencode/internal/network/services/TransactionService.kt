package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.domain.mapping.TransactionMetadataMapper
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.extensions.toModel
import com.getcode.opencode.model.core.errors.AirdropError
import com.getcode.opencode.model.core.errors.GetIntentMetadataError
import com.getcode.opencode.model.core.errors.GetLimitsError
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.core.errors.VoidGiftCardError
import com.getcode.opencode.model.core.errors.WithdrawalAvailabilityError
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.diff
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.coroutines.resume
import com.codeinc.opencode.gen.transaction.v2.TransactionService as RpcTransactionService

typealias OcpIntentStreamReference = BidirectionalStreamReference<SubmitIntentRequest, SubmitIntentResponse>

internal class TransactionService @Inject constructor(
    private val api: TransactionApi,
    private val metadataMapper: TransactionMetadataMapper,
) {
    suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = suspendCancellableCoroutine { cont ->
        trace(
            tag = "SubmitIntent",
            message = "Opening stream."
        )
        val streamReference = OcpIntentStreamReference(scope, "submitIntent")

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
    }

    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair
    ): Result<TransactionMetadata> {
        return runCatching {
            api.getIntentMetadata(intentId, owner)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.GetIntentMetadataResponse.Result.OK -> {
                        Result.success(metadataMapper.map(response.metadata))
                    }

                    TransactionService.GetIntentMetadataResponse.Result.NOT_FOUND -> Result.failure(
                        GetIntentMetadataError.NotFound()
                    )

                    TransactionService.GetIntentMetadataResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetIntentMetadataError.Unrecognized()
                    )

                    else -> Result.failure(GetIntentMetadataError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(GetIntentMetadataError.Other(cause = error))
            }
        )
    }

    suspend fun getLimits(
        owner: KeyPair,
        consumedSince: Instant,
    ): Result<Limits> {
        return runCatching {
            api.getLimits(owner, consumedSince.toEpochMilliseconds())
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.GetLimitsResponse.Result.OK -> {
                        val limits = Limits.newInstance(
                            sinceDate = consumedSince.toEpochMilliseconds(),
                            fetchDate = System.currentTimeMillis(),
                            sendLimits = response.sendLimitsByCurrencyMap,
                            buyLimits = response.buyModuleLimitsByCurrencyMap,
                            usdTransactedSinceConsumption = response.usdTransacted
                        )
                        Result.success(limits)
                    }

                    TransactionService.GetLimitsResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetLimitsError.Unrecognized()
                    )

                    else -> Result.failure(GetLimitsError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(GetLimitsError.Other(cause = error))
            }
        )
    }

    suspend fun withdrawalAvailability(
        destination: PublicKey,
    ): Result<WithdrawalAvailability> {
        return runCatching {
            api.canWithdrawToAccount(destination)
        }.fold(
            onSuccess = { response ->
                val availability = WithdrawalAvailability.newInstance(
                    destination = destination,
                    isValid = response.isValidPaymentDestination,
                    kind = WithdrawalAvailability.Kind.tryValueOf(response.accountType.name)
                        ?: WithdrawalAvailability.Kind.Unknown,
                    requiresInitialization = response.requiresInitialization,
                )

                Result.success(availability)
            },
            onFailure = { error ->
                Result.failure(WithdrawalAvailabilityError.Other(cause = error))
            }
        )
    }

    suspend fun airdrop(
        type: AirdropType,
        destination: KeyPair,
    ): Result<ExchangeData.WithRate> {
        return runCatching {
            api.airdrop(type, destination)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.AirdropResponse.Result.OK -> Result.success(response.exchangeData.toModel())
                    TransactionService.AirdropResponse.Result.UNAVAILABLE -> Result.failure(AirdropError.Unavailable())
                    TransactionService.AirdropResponse.Result.ALREADY_CLAIMED -> Result.failure(AirdropError.AlreadyClaimed())
                    TransactionService.AirdropResponse.Result.UNRECOGNIZED -> Result.failure(AirdropError.Unrecognized())
                    else -> Result.failure(AirdropError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(AirdropError.Other(cause = error))
            }
        )
    }

    suspend fun voidGiftCard(
        owner: KeyPair,
        giftCardVault: PublicKey,
    ): Result<Unit> {
        return runCatching {
            api.voidGiftCard(owner, giftCardVault)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.VoidGiftCardResponse.Result.OK -> Result.success(Unit)
                    TransactionService.VoidGiftCardResponse.Result.DENIED -> Result.failure(VoidGiftCardError.Denied())
                    TransactionService.VoidGiftCardResponse.Result.CLAIMED_BY_OTHER_USER -> Result.failure(VoidGiftCardError.AlreadyClaimed())
                    TransactionService.VoidGiftCardResponse.Result.NOT_FOUND -> Result.failure(VoidGiftCardError.NotFound())
                    TransactionService.VoidGiftCardResponse.Result.UNRECOGNIZED -> Result.failure(VoidGiftCardError.Unrecognized())
                    else -> Result.failure(VoidGiftCardError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(VoidGiftCardError.Other(cause = error))
            }
        )
    }

    private suspend fun openIntentStream(
        streamRef: OcpIntentStreamReference,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = openBidirectionalStream(
        streamRef = streamRef,
        apiCall = api::submitIntent,
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
                    val errors = handleErrors(intent, response.error.errorDetailsList)
                    trace(
                        tag = "SubmitIntent",
                        message = "Error: ${errors.joinToString("\n")}",
                        type = TraceType.Error
                    )
                    streamRef.complete()
                    onResult(Result.failure(SubmitIntentError.typed(response.error)))
                }

                SubmitIntentResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
                else -> Unit
            }
        }
    )
}

private fun handleServerParameters(
    intent: IntentType,
    serverParameters: List<TransactionService.ServerParameter>,
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
    errorDetails: List<RpcTransactionService.ErrorDetails>
): List<String> {
    val errors = mutableListOf<String>()

    errorDetails.forEach { error ->
        when (error.typeCase) {
            TransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                errors.add("Reason: ${error.reasonString.reason}")
            }

            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
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
                        "Android produced: $produced"
                    )
                )

                expected?.diff(produced)
            }

            TransactionService.ErrorDetails.TypeCase.DENIED -> {
                errors.add("Denied: ${error.denied.reason}")
            }

            else -> Unit
        }
    }

    return errors
}