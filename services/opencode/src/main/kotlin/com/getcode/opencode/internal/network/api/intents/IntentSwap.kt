package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.currencyCreatorParams
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.internal.network.extensions.verifiedMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

internal class IntentSwap(
    val request: SwapRequest,
    val metadata: VerifiedSwapMetadata,
){
    var parameters: SwapResponseServerParameters? = null

    fun sign(parameters: SwapResponseServerParameters): List<Signature> {
        val transaction = transaction(parameters)
        return when (parameters) {
            is SwapResponseServerParameters.ExistingCurrency -> {
                transaction.signatures(request.owner.authority.keyPair, request.swapAuthority)
            }
            is SwapResponseServerParameters.NewCurrency -> {
                // For new currency, owner == swapAuthority, so only 1 unique signature needed
                transaction.signatures(request.owner.authority.keyPair)
            }
        }
    }

    fun transaction(parameters: SwapResponseServerParameters): SolanaTransaction {
        return when (parameters) {
            is SwapResponseServerParameters.ExistingCurrency -> TransactionBuilder.swap(
                response = parameters,
                authority = request.owner.authorityPublicKey,
                swapAuthority = request.swapAuthority.toPublicKey(),
                direction = request.direction,
                amount = request.amount.underlyingTokenAmount.quarks,
            )
            is SwapResponseServerParameters.NewCurrency -> TransactionBuilder.buyNewCurrency(
                response = parameters,
                authority = request.owner.authorityPublicKey,
                coreMintMetadata = Token.usdf,
                amount = request.amount.underlyingTokenAmount.quarks,
            )
        }
    }

    fun initiate(): TransactionService.StatefulSwapRequest {
        val signer = request.owner.authority.keyPair
        return TransactionService.StatefulSwapRequest.newBuilder()
            .setInitiate(
                TransactionService.StatefulSwapRequest.Initiate.newBuilder()
                    .setOwner(request.owner.authorityPublicKey.asSolanaAccountId())
                    .setSwapAuthority(request.swapAuthority.toPublicKey().asSolanaAccountId())
                    .setReserve(request.currencyCreatorParams())
                    .apply {
                        val proofSignature = request.verifiedMetadata().sign(signer)
                        setProofSignature(proofSignature)
                    }
                    .apply { setSignature(sign(signer)) }
            ).build()

    }

    fun requestToSubmitSignatures(): TransactionService.StatefulSwapRequest {
        val params = parameters ?: throw IllegalStateException("parameters not set")

        return TransactionService.StatefulSwapRequest.newBuilder()
            .setSubmitSignatures(
                TransactionService.StatefulSwapRequest.SubmitSignatures.newBuilder()
                    .addAllTransactionSignatures(
                        sign(params).map { key -> key.asSignature() }
                    )
                    .build()
            ).build()
    }
}