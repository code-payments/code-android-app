package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.FundingSource
import com.getcode.opencode.model.transactions.StartSwapResult
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapState
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
import javax.inject.Inject
import kotlin.coroutines.resume

//typealias OcpStartSwapStreamReference = BidirectionalStreamReference<TransactionService.StartSwapRequest, TransactionService.StartSwapResponse>

internal class SwapStarter @Inject constructor(
    private val api: TransactionApi,
) {

    suspend fun start(
        scope: CoroutineScope,
        request: SwapRequest,
    ): StartSwapResult = suspendCancellableCoroutine { cont ->
        trace("swap id: ${request.swapId.publicKey.base58()}, from: ${request.direction.sourceMint.address.base58()}, to: ${request.direction.destinationMint.address.base58()}, amount: ${request.amount.nativeAmount.formatted()}")

        // Store client parameters for verification metadata construction
        val clientParameters = VerifiedSwapMetadata.ClientParameters(
            id = request.swapId,
            fromMint = request.direction.sourceMint.address,
            toMint = request.direction.destinationMint.address,
            amount = request.amount.underlyingTokenAmount,
            fundingSource = FundingSource.SUBMIT_INTENT,
            fundingId = request.fundingIntentId
        )

        cont.resume(Result.failure(NotImplementedError()))

//        val streamReference = OcpStartSwapStreamReference(scope, "start-swap")
//        streamReference.retain()
//        scope.launch {
//            try {
//                val result = openSwapStream(streamReference, request, clientParameters)
//                cont.resume(result)
//            } catch (e: Exception) {
//                trace(
//                    tag = "StartSwap",
//                    message = "Failed to open swap stream.",
//                    error = e
//                )
//
//                if (!cont.isCompleted) {
//                    cont.resume(Result.failure(SwapError.Other(cause = e)))
//                }
//            }
//        }
    }

//    private suspend fun openSwapStream(
//        streamRef: OcpStartSwapStreamReference,
//        request: SwapRequest,
//        clientParameters: VerifiedSwapMetadata.ClientParameters,
//    ): Result<SwapMetadata> {
//        var receivedServerParameters: VerifiedSwapMetadata.ServerParameters? = null
//        var verifiedMetadataSignature: Signature? = null
//
//        return openBidirectionalStream(
//            streamRef = streamRef,
//            apiCall = api::startSwap,
//            initialRequest = { request.start(clientParameters).build() },
//            responseHandler = { response, onResult, requestChannel ->
//                when (val result = response.responseCase) {
//                    TransactionService.StartSwapResponse.ResponseCase.SERVER_PARAMETERS -> {
//                        try {
//                            val params = VerifiedSwapMetadata.ServerParameters.fromServer(response.serverParameters)
//                            if (params != null) {
//                                receivedServerParameters = params
//                                trace(
//                                    tag = "StartSwap",
//                                    message = "Received ${params.javaClass.simpleName} parameters. Submitting signatures...",
//                                    type = TraceType.Silent
//                                )
//
//                                val verifiedMetadata = VerifiedSwapMetadata(
//                                    clientParameters = clientParameters,
//                                    serverParameters = receivedServerParameters
//                                )
//
//                                val data = verifiedMetadata.asProtobufMetadata().toByteArray()
//                                val signature = request.owner.authority.keyPair.sign(data) ?: throw IllegalStateException()
//
//                                verifiedMetadataSignature = Signature(signature.toList())
//
//                                val submitSignatures = TransactionService.StartSwapRequest.newBuilder()
//                                    .setSubmitSignature(
//                                        TransactionService.StartSwapRequest.SubmitSignature.newBuilder()
//                                            .setSignature(signature.asSignature())
//                                            .build()
//                                    ).build()
//
//                                requestChannel(submitSignatures)
//                            } else {
//                                onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid server parameters"))))
//                            }
//                        } catch (e: Exception) {
//                            if (BuildConfig.DEBUG) {
//                                e.printStackTrace()
//                            }
//                            onResult(Result.failure(SwapError.Other(cause = e)))
//                        }
//                    }
//
//                    TransactionService.StartSwapResponse.ResponseCase.SUCCESS -> {
//                        streamRef.complete()
//                        val serverParams = receivedServerParameters
//                        val signature = verifiedMetadataSignature
//
//                        if (serverParams == null || signature == null) {
//                            onResult(Result.failure(SwapError.Other(cause = IllegalStateException("Missing server parameters or signature"))))
//                            return@openBidirectionalStream
//                        }
//
//                        val metadata = SwapMetadata(
//                            verifiedMetadata = VerifiedSwapMetadata(
//                                clientParameters = clientParameters,
//                                serverParameters = serverParams
//                            ),
//                            state = SwapState.CREATED,
//                            signature = signature.bytes
//                        )
//
//                        onResult(Result.success(metadata))
//                    }
//
//                    TransactionService.StartSwapResponse.ResponseCase.ERROR -> {
//                        val errors = handleErrors(response.error.errorDetailsList)
//                        trace(
//                            tag = "StartSwap",
//                            message = "Error: ($request) ${errors.joinToString("\n")}",
//                            type = TraceType.Error
//                        )
//                        streamRef.complete()
//                        onResult(Result.failure(SwapError.typedFromStart(response.error)))
//                    }
//
//                    TransactionService.StartSwapResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
//                }
//            }
//        )
//    }
}

private fun handleErrors(
    errorDetails: List<TransactionService.ErrorDetails>
): List<String> {
    val errors = mutableListOf<String>()

    errorDetails.forEach { error ->
        when (error.typeCase) {
            TransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                errors.add("Reason: ${error.reasonString.reason}")
            }

            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                val expected = SolanaTransaction.fromList(error.invalidSignature.expectedTransaction.value.toByteArray().toList())
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
            }

            TransactionService.ErrorDetails.TypeCase.DENIED -> {
                errors.add("Denied: ${error.denied.reason}")
            }

            else -> Unit
        }
    }

    return errors
}