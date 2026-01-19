package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asSwapId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.transactions.SwapDirection
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

internal class IntentSwap(
    val id: SwapId,
    val owner: AccountCluster,
    val verifiedSwapMetadata: VerifiedSwapMetadata,
    val swapAuthority: Ed25519.KeyPair,
    val amountInQuarks: Long,
    val direction: SwapDirection,
    val waitForBlockchain: Boolean,
){
    var parameters: SwapResponseServerParameters? = null

    fun sign(parameters: SwapResponseServerParameters): List<Signature> {
        val transaction = transaction(parameters)
        return transaction.signatures(owner.authority.keyPair, swapAuthority)
    }

    fun transaction(parameters: SwapResponseServerParameters): SolanaTransaction {
        return TransactionBuilder.swap(
            response = parameters,
            metadata = verifiedSwapMetadata,
            authority = owner.authorityPublicKey,
            swapAuthority = swapAuthority.toPublicKey(),
            direction = direction,
            amount = amountInQuarks,
        )
    }

//    fun initiate(): TransactionService.SwapRequest {
//        return TransactionService.SwapRequest.newBuilder()
//            .setInitiate(
//                TransactionService.SwapRequest.Initiate.newBuilder()
//                    .setStateful(
//                        TransactionService.SwapRequest.Initiate.Stateful.newBuilder()
//                            .setSwapId(id.asSwapId())
//                            .setOwner(owner.authorityPublicKey.asSolanaAccountId())
//                            .setSwapAuthority(swapAuthority.toPublicKey().asSolanaAccountId())
//                            .apply { setSignature(sign(this@IntentSwap.owner.authority.keyPair)) }
//                    )
//            ).build()
//
//    }
//
//    fun requestToSubmitSignatures(): TransactionService.SwapRequest {
//        val params = parameters ?: throw IllegalStateException("parameters not set")
//
//        return TransactionService.SwapRequest.newBuilder()
//            .setSubmitSignatures(
//                TransactionService.SwapRequest.SubmitSignatures.newBuilder()
//                    .addAllSignatures(
//                        sign(params).map { key -> key.asSignature() }
//                    )
//                    .build()
//            ).build()
//    }
}