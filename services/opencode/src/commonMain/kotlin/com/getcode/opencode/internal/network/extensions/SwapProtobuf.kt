package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.api.intents.IntentStatefulSwap
import com.getcode.opencode.internal.network.api.intents.IntentStatelessSwap
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.model.transactions.SwapProgram
import com.getcode.opencode.model.transactions.SwapState

// The protobuf-java requests for the swap intents in :services:opencode-intents.

fun IntentStatefulSwap.initiate(): OcpTransactionService.StatefulSwapRequest {
    val signer = request.owner.authority.keyPair
    return OcpTransactionService.StatefulSwapRequest.newBuilder()
        .setInitiate(
            OcpTransactionService.StatefulSwapRequest.Initiate.newBuilder()
                .setOwner(request.owner.authorityPublicKey.asSolanaAccountId())
                .setSwapAuthority(request.swapAuthority.toPublicKey().asSolanaAccountId())
                .apply {
                    when (request.program) {
                        is SwapProgram.Reserve -> setReserve(request.currencyCreatorParams())
                        is SwapProgram.Stablecoin -> setStablecoin(request.stablecoinParams())
                    }
                    val proofSignature = request.verifiedMetadata().sign(signer)
                    setProofSignature(proofSignature)
                }
                .apply { setSignature(sign(signer)) }
        ).build()
}

fun IntentStatefulSwap.requestToSubmitSignatures(): OcpTransactionService.StatefulSwapRequest {
    val params = parameters ?: throw IllegalStateException("parameters not set")
    return OcpTransactionService.StatefulSwapRequest.newBuilder()
        .setSubmitSignatures(
            OcpTransactionService.StatefulSwapRequest.SubmitSignatures.newBuilder()
                .addAllTransactionSignatures(
                    sign(params).map { key -> key.asSignature() }
                )
                .build()
        ).build()
}

internal fun IntentStatelessSwap.stablecoinParams(): OcpTransactionService.StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters =
    OcpTransactionService.StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
        .setFromMint(request.fromMint.asSolanaAccountId())
        .setToMint(request.toMint.asSolanaAccountId())
        .setSwapAmount(request.amount)
        // Same constant the transaction builder defaults to, so request and local build agree.
        .setSwapVersion(CoinbaseStableSwapperProgram.SERVER_SWAP_VERSION)
        .build()

fun IntentStatelessSwap.initiate(): OcpTransactionService.StatelessSwapRequest {
    val owner = request.owner.authority.keyPair
    val initiate = OcpTransactionService.StatelessSwapRequest.Initiate.newBuilder()
        .setOwner(owner.asSolanaAccountId())
        .setStablecoin(stablecoinParams())
        .setWaitForFinalization(request.waitForFinalization)
        .apply { setSignature(sign(owner)) }
        .build()
    return OcpTransactionService.StatelessSwapRequest.newBuilder()
        .setInitiate(initiate)
        .build()
}

fun IntentStatelessSwap.requestToSubmitSignatures(): OcpTransactionService.StatelessSwapRequest {
    val params = parameters ?: throw IllegalStateException("parameters not set")
    return OcpTransactionService.StatelessSwapRequest.newBuilder()
        .setSubmitSignatures(
            OcpTransactionService.StatelessSwapRequest.SubmitSignatures.newBuilder()
                .addAllTransactionSignatures(sign(params).map { it.asSignature() })
                .build()
        )
        .build()
}

fun SwapState.Companion.tryValueOf(value: OcpTransactionService.SwapMetadata): SwapState =
    SwapState.entries.getOrNull(value.stateValue) ?: SwapState.UNKNOWN
