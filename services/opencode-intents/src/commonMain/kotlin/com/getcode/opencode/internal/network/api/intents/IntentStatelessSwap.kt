package com.getcode.opencode.internal.network.api.intents

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.StatelessSwapRequest
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

@InternalOpenCodeApi
class IntentStatelessSwap(
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
}
