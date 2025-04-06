package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.domain.mapping.TransactionMetadataMapper
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.intents.IntentType
import com.getcode.opencode.internal.intents.ServerParameter
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.internal.network.extensions.toModel
import com.getcode.opencode.internal.network.managedApiRequest
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.Limits
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.observers.BidirectionalStreamReference
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.base58
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import io.grpc.stub.StreamObserver
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
    private val networkOracle: NetworkOracle,
    private val metadataMapper: TransactionMetadataMapper,
) {
    suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): OcpIntentStreamReference {
        trace(
            tag = "SubmitIntent",
            message = "Opening stream."
        )
        val streamReference = OcpIntentStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace(
                tag = "SubmitIntent",
                message = "Stream timed out"
            )
            scope.launch {
                openIntentStream(
                    streamRef = streamReference,
                    intent = intent,
                    owner = owner,
                )
            }
        }

        openIntentStream(streamReference, intent, owner)

        return streamReference
    }

    suspend fun getIntentMetadata(
        intentId: ID,
        owner: KeyPair
    ): Result<TransactionMetadata> {
        return networkOracle.managedApiRequest(
            call = { api.getIntentMetadata(intentId, owner) },
            handleResponse = { response ->
                when (response.result) {
                    TransactionService.GetIntentMetadataResponse.Result.OK -> Result.success(
                        metadataMapper.map(response.metadata)
                    )

                    TransactionService.GetIntentMetadataResponse.Result.NOT_FOUND -> Result.failure(
                        GetIntentMetadataError.NotFound()
                    )

                    TransactionService.GetIntentMetadataResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetIntentMetadataError.Unrecognized()
                    )

                    else -> Result.failure(GetIntentMetadataError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(GetIntentMetadataError.Other(cause = error))
            }
        )
    }

    suspend fun getLimits(
        owner: KeyPair,
        consumedSince: Instant,
    ): Result<Limits> {
        return networkOracle.managedApiRequest(
            call = { api.getLimits(owner, consumedSince.toEpochMilliseconds()) },
            handleResponse = { response ->
                when (response.result) {
                    TransactionService.GetLimitsResponse.Result.OK -> {
                        val limits = Limits.newInstance(
                            sinceDate = consumedSince.toEpochMilliseconds(),
                            fetchDate = System.currentTimeMillis(),
                            sendLimits = response.sendLimitsByCurrencyMap,
                            buyLimits = response.buyModuleLimitsByCurrencyMap,
                            deposits = response.depositLimit
                        )
                        Result.success(limits)
                    }

                    TransactionService.GetLimitsResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetLimitsError.Unrecognized()
                    )

                    else -> Result.failure(GetLimitsError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(GetLimitsError.Other(cause = error))
            }
        )
    }

    suspend fun withdrawalAvailability(
        account: KeyPair
    ): Result<WithdrawalAvailability> {
        return networkOracle.managedApiRequest(
            call = { api.canWithdrawToAccount(account) },
            handleResponse = { response ->
                val availability = WithdrawalAvailability.newInstance(
                    destination = account.publicKeyBytes.toPublicKey(),
                    isValid = response.isValidPaymentDestination,
                    kind = WithdrawalAvailability.Kind.tryValueOf(response.accountType.name)
                        ?: WithdrawalAvailability.Kind.Unknown
                )

                Result.success(availability)
            },
            onOtherError = { error ->
                Result.failure(WithdrawalAvailabilityError.Other(cause = error))
            },
        )
    }

    suspend fun airdrop(
        type: AirdropType,
        destination: KeyPair,
    ): Result<ExchangeData.WithRate> {
        return networkOracle.managedApiRequest(
            call = { api.airdrop(type, destination) },
            handleResponse = { response ->
                when (response.result) {
                    TransactionService.AirdropResponse.Result.OK -> {
                        Result.success(response.exchangeData.toModel())
                    }
                    TransactionService.AirdropResponse.Result.UNAVAILABLE -> Result.failure(AirdropError.Unavailable())
                    TransactionService.AirdropResponse.Result.ALREADY_CLAIMED -> Result.failure(AirdropError.AlreadyClaimed())
                    TransactionService.AirdropResponse.Result.UNRECOGNIZED -> Result.failure(AirdropError.Unrecognized())
                    else -> Result.failure(AirdropError.Other())
                }
            },
            onOtherError = { error -> Result.failure(AirdropError.Other(cause = error)) }
        )
    }

    private suspend fun openIntentStream(
        streamRef: OcpIntentStreamReference,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = suspendCancellableCoroutine { cont ->
        try {
            streamRef.cancel()
            streamRef.stream = api.submitIntent(object : StreamObserver<SubmitIntentResponse> {
                override fun onNext(value: SubmitIntentResponse?) {
                    when (value?.responseCase) {
                        SubmitIntentResponse.ResponseCase.SERVER_PARAMETERS -> {
                            handleServerParameters(
                                intent = intent,
                                stream = streamRef.stream,
                                serverParameters = value.serverParameters.serverParametersList
                            )
                        }

                        SubmitIntentResponse.ResponseCase.SUCCESS -> {
                            streamRef.stream?.onCompleted()
                            cont.resume(Result.success(intent))
                        }

                        SubmitIntentResponse.ResponseCase.ERROR -> {
                            val errors = handleErrors(intent, value.error.errorDetailsList)
                            trace(
                                tag = "SubmitIntent",
                                message = "Error: ${errors.joinToString("\n")}",
                                type = TraceType.Error
                            )

                            streamRef.stream?.onCompleted()
                            cont.resume(
                                Result.failure(SubmitIntentError.typed(value.error))
                            )
                        }

                        SubmitIntentResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
                        else -> Unit
                    }
                }

                override fun onError(t: Throwable?) {
                    streamRef.cancel()
                }

                override fun onCompleted() {
                    trace(
                        tag = "SubmitIntent",
                        message = "Completed",
                        type = TraceType.Silent
                    )
                }
            })

            streamRef.stream?.onNext(intent.requestToSubmitActions(owner))
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }
}

private fun handleServerParameters(
    intent: IntentType,
    stream: StreamObserver<SubmitIntentRequest>?,
    serverParameters: List<TransactionService.ServerParameter>,
) {
    try {
        intent.apply(serverParameters.map { p -> ServerParameter.newInstance(p) })

        trace(
            tag = "SubmitIntent",
            message = "Received ${serverParameters.size} parameters. Submitting signatures...",
            type = TraceType.Silent
        )

        val submitSignatures = intent.requestToSubmitSignatures()
        stream?.onNext(submitSignatures)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            e.printStackTrace()
        }
        trace(
            tag = "SubmitIntent",
            message = "Received ${serverParameters.size} parameters but failed to apply them: ${e.javaClass.simpleName} ${e.message})",
            type = TraceType.Silent
        )
        stream?.onError(SubmitIntentError.Other(cause = e))
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
                errors.add("Reason: ${error.reasonString}")
            }

            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                val expectedVixn =
                    error.invalidSignature.expectedVixnHash.value.toByteArray()
                val produced = intent.vixnHash()
                errors.add("Signature mismatch: :: VIXN:: expected=${expectedVixn.base58}, produced=${produced.base58()}")
            }

            TransactionService.ErrorDetails.TypeCase.DENIED -> {
                errors.add("Denied: ${error.denied.reason}")
            }

            else -> Unit
        }
    }

    return errors
}

enum class DeniedReason {
    Unspecified,
    TooManyFreeAccountsForPhoneNumber,
    TooManyFreeAccountsForDevice,
    UnsupportedCountry,
    UnsupportedDevice;

    companion object {
        fun fromValue(value: Int): DeniedReason {
            return entries.firstOrNull { it.ordinal == value } ?: Unspecified
        }
    }
}

sealed class SubmitIntentError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class InvalidIntent(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    class Signature : SubmitIntentError()
    data class StaleState(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    data class Denied(private val reasons: List<DeniedReason>) :
        SubmitIntentError(message = reasons.joinToString())

    class Unrecognized : SubmitIntentError()
    data class Other(override val cause: Throwable? = null) : SubmitIntentError()

    companion object {
        fun typed(proto: SubmitIntentResponse.Error): SubmitIntentError {
            val reasonStrings = proto.errorDetailsList.mapNotNull {
                when (it.typeCase) {
                    TransactionService.ErrorDetails.TypeCase.REASON_STRING ->
                        it.reasonString.reason.takeIf { reason -> reason.isNotEmpty() }

                    else -> null
                }
            }
            return when (proto.code) {
                SubmitIntentResponse.Error.Code.DENIED -> {
                    val reasons = proto.errorDetailsList.mapNotNull {
                        if (!it.hasDenied()) return@mapNotNull null
                        DeniedReason.fromValue(it.denied.codeValue)
                    }

                    Denied(reasons)
                }

                SubmitIntentResponse.Error.Code.INVALID_INTENT -> InvalidIntent(reasonStrings)
                SubmitIntentResponse.Error.Code.SIGNATURE_ERROR -> Signature()
                SubmitIntentResponse.Error.Code.STALE_STATE -> StaleState(reasonStrings)
                SubmitIntentResponse.Error.Code.UNRECOGNIZED -> Unrecognized()
                else -> return Other()
            }
        }
    }
}

sealed class GetIntentMetadataError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetIntentMetadataError()
    class Unrecognized : GetIntentMetadataError()
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError()
}

sealed class GetLimitsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetIntentMetadataError()
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError()
}

sealed class WithdrawalAvailabilityError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : WithdrawalAvailabilityError()
}

sealed class AirdropError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unavailable: AirdropError()
    class AlreadyClaimed: AirdropError()
    class Unrecognized: AirdropError()
    data class Other(override val cause: Throwable? = null) : AirdropError()
}