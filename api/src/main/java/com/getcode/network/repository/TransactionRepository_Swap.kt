package com.getcode.network.repository

import com.codeinc.gen.transaction.v2.TransactionService.SwapRequest
import com.codeinc.gen.transaction.v2.TransactionService.SwapResponse
import com.getcode.model.intents.SwapConfigParameters
import com.getcode.model.intents.SwapIntent
import com.getcode.model.intents.requestToSubmitSignatures
import com.getcode.network.core.BidirectionalStreamReference
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.Organizer
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

typealias BidirectionalSwapStream = BidirectionalStreamReference<SwapRequest, SwapResponse>

suspend fun TransactionRepository.initiateSwap(organizer: Organizer): Result<SwapIntent> {
    val intent = SwapIntent.newInstance(context, organizer)
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
                        Timber.e(t = e, message ="Received parameters but failed to apply them. Intent: ${intent.id.base58()}")
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
                    val errorReason =
                        value.error.errorDetailsList.firstOrNull()?.reasonString?.reason.orEmpty()
                    value.error.errorDetailsList.forEach { error ->
                        Timber.e(
                            "Error: ${error.reasonString.reason} | ${error.typeCase.name}"
                        )
                    }

                    reference.stream?.onCompleted()
                    cont.resume(
                        Result.failure(
                            TransactionRepository.ErrorSubmitIntentException(
                                TransactionRepository.ErrorSubmitIntent.fromValue(value.error.codeValue),
                                null,
                                errorReason
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
            cont.resume(
                Result.failure(
                    TransactionRepository.ErrorSubmitIntentException(
                        TransactionRepository.ErrorSubmitIntent.Unknown, t
                    )
                )
            )
        }

        override fun onCompleted() {
            Timber.i("onCompleted")
        }
    })

    reference.stream?.onNext(intent.requestToSubmitSignatures())
}