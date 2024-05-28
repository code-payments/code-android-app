package com.getcode.network.repository

import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.SwapRequest
import com.codeinc.gen.transaction.v2.TransactionService.SwapRequest.Initiate
import com.codeinc.gen.transaction.v2.TransactionService.SwapResponse
import com.getcode.model.intents.SwapConfigParameters
import com.getcode.model.intents.SwapIntent
import com.getcode.model.intents.requestToSubmitSignatures
import com.getcode.network.core.BidirectionalStreamReference
import com.getcode.network.repository.TransactionRepository.ErrorSubmitIntent
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.diff
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.ErrorUtils
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kin.sdk.base.tools.Base58
import timber.log.Timber
import kotlin.coroutines.resume

typealias BidirectionalSwapStream = BidirectionalStreamReference<SwapRequest, SwapResponse>

suspend fun TransactionRepository.initiateSwap(organizer: Organizer): Result<SwapIntent> {
    val intent = SwapIntent.newInstance(organizer)

    Timber.d("Swap ID: ${intent.id.base58()}")

    return submit(intent)
}

private suspend fun TransactionRepository.submit(intent: SwapIntent): Result<SwapIntent> = suspendCancellableCoroutine { cont ->
    val reference = BidirectionalSwapStream()

    // Intentionally creates a retain-cycle using closures to ensure that we have
    // a strong reference to the stream at all times. Doing so ensures that the
    // callers don't have to manage the pointer to this stream and keep it alive
    reference.retain()

    reference.stream = transactionApi.swap(object : StreamObserver<SwapResponse> {
        override fun onNext(value: SwapResponse?) {
            when (val response = value?.responseCase) {
                // 2. Upon successful submission of intent action the server will
                // respond with parameters that we'll need to apply to the intent
                // before crafting and signing the transactions.
                SwapResponse.ResponseCase.SERVER_PARAMETERS -> {
                    try {
                        val configParameters = SwapConfigParameters.invoke(value.serverParameters) ?: throw IllegalArgumentException()
                        intent.parameters = configParameters

                        val submitSignatures = intent.requestToSubmitSignatures()
                        reference.stream?.onNext(submitSignatures)
                        Timber.d("Sent swap request, Intent: ${intent.id.base58()}")
                    } catch (e: Exception) {
                        Timber.e(t = e, message = "Received parameters but failed to apply them. Intent: ${intent.id.base58()}")
                        cont.resume(Result.failure(e))
                    }
                }

                // 3. If submitted transaction signatures are valid and match
                // the server, we'll receive a success for the submitted intent.
                SwapResponse.ResponseCase.SUCCESS -> {
                    Timber.d("Success: ${value.success.codeValue}, Intent: ${intent.id.base58()}")
                    reference.stream?.onCompleted()
                }

                // 3. If the submitted transaction signatures don't match, the
                // intent is considered failed. Something must have gone wrong
                // on the transaction creation or signing on our side.
                SwapResponse.ResponseCase.ERROR -> {

                    val errors = mutableListOf<String>()

                    value.error.errorDetailsList.forEach { error ->
                        when (error.typeCase) {
                            TransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                                errors.add("Reason: ${error.reasonString}")
                            }
                            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                                val expected = SolanaTransaction.fromList(error.invalidSignature.expectedTransaction.value.toByteArray().toList())
                                val produced = intent.transaction(intent.parameters!!)
                                errors.addAll(
                                    listOf(
                                        "Action index: ${error.invalidSignature.actionId}",
                                        "Invalid signature: ${Signature(error.invalidSignature.providedSignature.value.toByteArray().toList()).base58()}",
                                        "Transaction bytes: ${error.invalidSignature.expectedTransaction.value}",
                                        "Transaction expected: $expected",
                                        "Android produced: $produced"
                                    )
                                )

                                expected?.diff(produced)
                            }
                            TransactionService.ErrorDetails.TypeCase.INTENT_DENIED -> {
                                errors.add("Denied: ${error.intentDenied.reason.name}")
                            }
                            else -> Unit
                        }

                        Timber.e(
                            "Error: ${errors.joinToString("\n")}"
                        )
                    }

                    reference.stream?.onCompleted()
                    cont.resume(
                        Result.failure(
                            ErrorSubmitSwapIntentException(
                                ErrorSubmitSwapIntent.valueOf(value.error.codeValue),
                            )
                        )
                    )
                }
                else -> {
                    reference.stream?.onCompleted()
                }
            }
        }

        override fun onError(t: Throwable?) {
            Timber.i("onError: " + t?.message.orEmpty())
            t?.let {
                ErrorUtils.handleError(it)
            }
            cont.resume(
                Result.failure(
                    ErrorSubmitSwapIntentException(ErrorSubmitSwapIntent.Unknown, t)
                )
            )
        }

        override fun onCompleted() {
            Timber.i("onCompleted")
        }
    })

    val initiateSwap = SwapRequest.newBuilder()
        .setInitiate(Initiate.newBuilder()
            .setOwner(intent.owner.publicKeyBytes.toSolanaAccount())
            .setSwapAuthority(intent.swapCluster.authorityPublicKey.byteArray.toSolanaAccount())
            .apply { setSignature(sign(intent.owner)) }
            .build()
        ).build()

    reference.stream?.onNext(initiateSwap)
}

class ErrorSubmitSwapIntentException(
    val errorSubmitSwapIntent: ErrorSubmitSwapIntent,
    cause: Throwable? = null,
    val messageString: String = ""
) : Exception(cause) {
    override val message: String
        get() = "${errorSubmitSwapIntent.name} $messageString"
}

enum class ErrorSubmitSwapIntent(val value: Int) {
    Denied(0),
    InvalidIntent(1),
    SignatureError(2),
    StaleState(3),
    Unknown(-1),
    DeviceTokenUnavailable(-2);

    companion object {
        fun valueOf(value: Int): ErrorSubmitSwapIntent {
            return entries.firstOrNull { it.value == value } ?: Unknown
        }
    }
}
