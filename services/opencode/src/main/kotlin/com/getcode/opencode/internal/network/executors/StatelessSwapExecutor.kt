package com.getcode.opencode.internal.network.executors

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStreamForResult
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.internal.network.extensions.toCode
import com.getcode.opencode.internal.network.extensions.toProps
import com.getcode.opencode.internal.network.extensions.toSignature
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.model.transactions.StatelessSwapResult
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.opencode.model.transactions.StatelessSwapSuccess
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Mint
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

private typealias StatelessSwapStreamReference =
    BidirectionalStreamReference<TransactionService.StatelessSwapRequest, TransactionService.StatelessSwapResponse>

internal class StatelessSwapExecutor(
    private val api: TransactionApi,
) {
    private val streamReferenceMutex = Mutex()

    suspend fun execute(
        scope: CoroutineScope,
        owner: KeyPair,
        fromMint: Mint,
        toMint: Mint,
        amount: Long,
        waitForFinalization: Boolean = false,
        buildAndSign: (StatelessSwapServerParameters) -> Pair<SolanaTransaction, List<Signature>>,
    ): StatelessSwapResult = suspendCancellableCoroutine { cont ->
        trace(tag = TAG, message = "Opening stateless swap stream")

        val streamReference = StatelessSwapStreamReference(scope, "stateless-swap")
        streamReference.retain()

        scope.launch {
            try {
                val result = openStream(
                    streamRef = streamReference,
                    owner = owner,
                    fromMint = fromMint,
                    toMint = toMint,
                    amount = amount,
                    waitForFinalization = waitForFinalization,
                    buildAndSign = buildAndSign,
                )
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
        owner: KeyPair,
        fromMint: Mint,
        toMint: Mint,
        amount: Long,
        waitForFinalization: Boolean,
        buildAndSign: (StatelessSwapServerParameters) -> Pair<SolanaTransaction, List<Signature>>,
    ): StatelessSwapResult = openBidirectionalStreamForResult(
        streamRef = streamRef,
        apiCall = api::statelessSwap,
        initialRequest = {
            buildInitiateRequest(owner, fromMint, toMint, amount, waitForFinalization)
        },
        responseHandler = { response, onResult, requestChannel ->
            when (response.responseCase) {
                TransactionService.StatelessSwapResponse.ResponseCase.SERVER_PARAMETERS -> {
                    handleServerParameters(
                        serverParameters = response.serverParameters,
                        buildAndSign = buildAndSign,
                        onResult = onResult,
                        requestChannel = requestChannel,
                    )
                }

                TransactionService.StatelessSwapResponse.ResponseCase.SUCCESS -> {
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

                TransactionService.StatelessSwapResponse.ResponseCase.ERROR -> {
                    val errors = handleErrors(response.error.errorDetailsList)
                    trace(
                        tag = TAG,
                        message = "Error: (${response.error.code}) ${errors.joinToString("\n")}",
                        type = TraceType.Error,
                    )
                    streamRef.complete()
                    onResult(Result.failure(SwapError.typed(response.error)))
                }

                TransactionService.StatelessSwapResponse.ResponseCase.RESPONSE_NOT_SET -> Unit
            }
        }
    )
}

private fun buildInitiateRequest(
    owner: KeyPair,
    fromMint: Mint,
    toMint: Mint,
    amount: Long,
    waitForFinalization: Boolean,
): TransactionService.StatelessSwapRequest {
    val initiate = TransactionService.StatelessSwapRequest.Initiate.newBuilder()
        .setOwner(owner.asSolanaAccountId())
        .setStablecoin(
            TransactionService.StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                .setFromMint(fromMint.asSolanaAccountId())
                .setToMint(toMint.asSolanaAccountId())
                .setSwapAmount(amount)
                .build()
        )
        .setWaitForFinalization(waitForFinalization)
        .apply { setSignature(sign(owner)) }
        .build()

    return TransactionService.StatelessSwapRequest.newBuilder()
        .setInitiate(initiate)
        .build()
}

private fun handleServerParameters(
    serverParameters: TransactionService.StatelessSwapResponse.ServerParameters?,
    buildAndSign: (StatelessSwapServerParameters) -> Pair<SolanaTransaction, List<Signature>>,
    requestChannel: (TransactionService.StatelessSwapRequest) -> Unit,
    onResult: (StatelessSwapResult) -> Unit,
) {
    try {
        val params = when (serverParameters?.kindCase) {
            TransactionService.StatelessSwapResponse.ServerParameters.KindCase.STABLECOIN ->
                serverParameters.stablecoin.toProps()
            TransactionService.StatelessSwapResponse.ServerParameters.KindCase.KIND_NOT_SET,
            null -> null
        }

        if (params == null) {
            onResult(Result.failure(SwapError.Other(cause = IllegalArgumentException("Invalid server parameters"))))
            return
        }

        trace(tag = TAG, message = "Received server parameters. Building transaction and submitting signatures...")

        val (_, signatures) = buildAndSign(params)

        val submitSignatures = TransactionService.StatelessSwapRequest.SubmitSignatures.newBuilder()
            .addAllTransactionSignatures(signatures.map { it.asSignature() })
            .build()

        requestChannel(
            TransactionService.StatelessSwapRequest.newBuilder()
                .setSubmitSignatures(submitSignatures)
                .build()
        )
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) e.printStackTrace()
        onResult(Result.failure(SwapError.Other(cause = e)))
    }
}

private fun handleErrors(
    errorDetails: List<TransactionService.ErrorDetails>
): List<String> {
    val errors = mutableListOf<String>()
    errorDetails.forEach { error ->
        when (error.typeCase) {
            TransactionService.ErrorDetails.TypeCase.REASON_STRING ->
                errors.add("Reason: ${error.reasonString.reason}")
            TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE ->
                errors.add("Action index: ${error.invalidSignature.actionId}")
            TransactionService.ErrorDetails.TypeCase.DENIED ->
                errors.add("Denied: ${error.denied.reason}")
            else -> Unit
        }
    }
    return errors
}

private const val TAG = "StatelessSwap"
