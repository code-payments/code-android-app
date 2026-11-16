package com.getcode.opencode.internal.network.api.intents

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.model.transactions.SwapProgram
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

@InternalOpenCodeApi
class IntentStatefulSwap(
    val request: StatefulSwapRequest,
    val metadata: VerifiedSwapMetadata,
){
    var parameters: StatefulSwapResponseServerParameters? = null

    fun sign(parameters: StatefulSwapResponseServerParameters): List<Signature> {
        val transaction = transaction(parameters)
        return when (parameters) {
            is StatefulSwapResponseServerParameters.ExistingCurrency -> {
                transaction.signatures(request.owner.authority.keyPair, request.swapAuthority)
            }
            is StatefulSwapResponseServerParameters.NewCurrency -> {
                // For new currency, owner == swapAuthority, so only 1 unique signature needed
                transaction.signatures(request.owner.authority.keyPair)
            }

            is StatefulSwapResponseServerParameters.Stablecoin -> {
                transaction.signatures(request.owner.authority.keyPair, request.swapAuthority)
            }
        }
    }

    fun transaction(parameters: StatefulSwapResponseServerParameters): SolanaTransaction {
        return when (parameters) {
            is StatefulSwapResponseServerParameters.ExistingCurrency -> TransactionBuilder.swap(
                response = parameters,
                authority = request.owner.authorityPublicKey,
                swapAuthority = request.swapAuthority.toPublicKey(),
                route = request.route,
                amount = request.swapAmount.underlyingTokenAmount.quarks,
                // The fee we quoted the server in the initiate request. Omitting it built the
                // no-fee VM::TransferForSwap while the server built VM::TransferForSwapWithFee,
                // so the signatures never matched and every fee-bearing buy failed.
                feeAmount = request.feeAmount?.underlyingTokenAmount?.quarks ?: 0,
            )
            is StatefulSwapResponseServerParameters.NewCurrency -> TransactionBuilder.buyNewCurrency(
                response = parameters,
                authority = request.owner.authorityPublicKey,
                sourceMintMetadata = request.route.sourceMint,
                coreMintMetadata = Token.usdf,
                amount = request.swapAmount.underlyingTokenAmount.quarks,
                feeAmount = request.feeAmount?.underlyingTokenAmount?.quarks,
            )

            is StatefulSwapResponseServerParameters.Stablecoin -> TransactionBuilder.stablecoinSwap(
                response = parameters,
                authority = request.owner.authorityPublicKey,
                swapAuthority = request.swapAuthority.toPublicKey(),
                destinationOwner = (request.program as SwapProgram.Stablecoin).destinationOwner,
                route = request.route,
                amount = request.swapAmount.underlyingTokenAmount.quarks,
                feeAmount = request.feeAmount?.underlyingTokenAmount?.quarks ?: 0,
                // server expects 1:1 for stablecoins
                minOutput = request.swapAmount.underlyingTokenAmount.quarks,
            )
        }
    }
}
