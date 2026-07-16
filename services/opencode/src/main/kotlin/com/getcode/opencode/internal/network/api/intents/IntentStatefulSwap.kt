package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.extensions.asSignature
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.currencyCreatorParams
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.internal.network.extensions.stablecoinParams
import com.getcode.opencode.internal.network.extensions.verifiedMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.model.transactions.SwapProgram
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Signature

internal class IntentStatefulSwap(
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

    fun initiate(): OcpTransactionService.StatefulSwapRequest {
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

    fun requestToSubmitSignatures(): OcpTransactionService.StatefulSwapRequest {
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
}