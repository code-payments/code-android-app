package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.StatelessSwapRequest
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

internal class IntentStatelessSwap(
    val request: StatelessSwapRequest,
) {
    var parameters: StatelessSwapServerParameters? = null

    fun sign(parameters: StatelessSwapServerParameters): List<Signature> {
        val transaction = transaction(parameters)
        return transaction.signatures(request.owner.authority.keyPair)
    }

    fun transaction(parameters: StatelessSwapServerParameters): SolanaTransaction {
        return TransactionBuilder.statelessSwap(
            response = parameters,
            owner = request.owner.authorityPublicKey,
            fromMint = MintMetadata.usdc,
            toMint = MintMetadata.usdf,
            amount = request.amount,
        )
    }

    fun initiate(): TransactionService.StatelessSwapRequest {
        val owner = request.owner.authority.keyPair
        val initiate = TransactionService.StatelessSwapRequest.Initiate.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setStablecoin(
                TransactionService.StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                    .setFromMint(request.fromMint.asSolanaAccountId())
                    .setToMint(request.toMint.asSolanaAccountId())
                    .setSwapAmount(request.amount)
                    .build()
            )
            .setWaitForFinalization(request.waitForFinalization)
            .apply { setSignature(sign(owner)) }
            .build()

        return TransactionService.StatelessSwapRequest.newBuilder()
            .setInitiate(initiate)
            .build()
    }

    fun requestToSubmitSignatures(): TransactionService.StatelessSwapRequest {
        val params = parameters ?: throw IllegalStateException("parameters not set")

        return TransactionService.StatelessSwapRequest.newBuilder()
            .setSubmitSignatures(
                TransactionService.StatelessSwapRequest.SubmitSignatures.newBuilder()
                    .addAllTransactionSignatures(sign(params).map { it.asSignature() })
                    .build()
            )
            .build()
    }
}
