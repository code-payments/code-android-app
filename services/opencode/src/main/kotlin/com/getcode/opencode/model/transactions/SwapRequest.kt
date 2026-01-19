package com.getcode.opencode.model.transactions

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey


sealed interface InitiateSwap {
    val owner: AccountCluster
    val swapAuthority: KeyPair

    /**
     * @param owner The verified owner account public key
     * @param fromMint The source mint that will be swapped from
     * @param toMint The destination mint that will be swapped to
     * @param amount The amount to swap from the source mint in quarks.
     * @param swapAuthority The user authority account that will sign to authorize the swap.
     * For Currency Creator program buy/sell flows, this should be a random one-time use account.
     * @param waitForBlockchainStatus Whether the client wants the RPC to wait for blockchain status. If false,
     * then the RPC will return Success when the swap is submitted to the blockchain.
     * Otherwise, the RPC will observe and report back the status of the transaction.
     */
    data class Stateless(
        override val owner: AccountCluster,
        val fromMint: PublicKey,
        val toMint: PublicKey,
        val amount: Long,
        override val swapAuthority: KeyPair,
        val waitForBlockchainStatus: Boolean,
    ) : InitiateSwap

    /**
     * @param swapId The ID of the swap to execute
     * @param owner TThe verified owner account public key
     * @param swapAuthority The user authority account that will sign to authorize the swap.
     * For Currency Creator program buy/sell flows, this should be a random one-time use account.
     */
    data class Stateful(
        val swapId: SwapId,
        override val owner: AccountCluster,
        override val swapAuthority: KeyPair,
    ) : InitiateSwap

//    fun start(
//        clientParameters: VerifiedSwapMetadata.ClientParameters
//    ): TransactionService.StartSwapRequest.Builder {
//        val request = TransactionService.StartSwapRequest.Start.newBuilder()
//            .setOwner(owner.authorityPublicKey.asSolanaAccountId())
//            .setCurrencyCreator(clientParameters.asProtobufParameters())
//            .apply { setSignature(sign(this@InitiateSwap.owner.authority.keyPair))  }
//            .build()
//
//        return TransactionService.StartSwapRequest.newBuilder()
//            .setStart(request)
//    }
//
//    fun execute(): TransactionService.SwapRequest.Builder {
//        val request = TransactionService.SwapRequest.Initiate.newBuilder()
//            .apply {
//                when (this@InitiateSwap) {
//                    is Stateful -> setStateful(
//                        TransactionService.SwapRequest.Initiate.Stateful.newBuilder()
//                            .setOwner(owner.authorityPublicKey.asSolanaAccountId())
//                            .setSwapAuthority(swapAuthority.asSolanaAccountId())
//                            .setSwapId(swapId.asSwapId())
//                            .apply { setSignature(sign(this@InitiateSwap.owner.authority.keyPair)) }
//                    )
//
//                    is Stateless -> setStateless(
//                        TransactionService.SwapRequest.Initiate.Stateless.newBuilder()
//                            .setOwner(owner.authorityPublicKey.asSolanaAccountId())
//                            .setFromMint(fromMint.asSolanaAccountId())
//                            .setToMint(fromMint.asSolanaAccountId())
//                            .setAmount(amount)
//                            .setSwapAuthority(swapAuthority.asSolanaAccountId())
//                            .setWaitForBlockchainStatus(waitForBlockchainStatus)
//                            .apply { setSignature(sign(this@InitiateSwap.owner.authority.keyPair)) }
//                    )
//                }
//            }.build()
//
//        return TransactionService.SwapRequest.newBuilder()
//            .setInitiate(request)
//    }
}

data class SwapRequest(
    val params: InitiateSwap,
    val direction: SwapDirection,
    val amount: LocalFiat,
) {
    val swapId = when (params) {
        is InitiateSwap.Stateful -> params.swapId
        is InitiateSwap.Stateless -> SwapId.generate()
    }

    val fundingIntentId = PublicKey.generate()
    val swapAuthority = params.swapAuthority
    val owner = params.owner

//    fun start(clientParameters: VerifiedSwapMetadata.ClientParameters): TransactionService.StartSwapRequest.Builder {
//        return params.start(clientParameters)
//    }
//
//    fun execute(): TransactionService.SwapRequest.Builder {
//        return params.execute()
//    }
}

sealed interface SwapStartKind {
    /**
     * Server parameters for starting swaps against the Currency Creator program
     */
    data class CurrencyCreator(
        /**
         * The unique ID for this swap randomly generated on client
         */
        val swapId: ID,
        /**
         * The source mint that will be swapped from
         */
        val fromMint: PublicKey,
        /**
         * The destination mint that will be swapped from
         */
        val toMint: PublicKey,
        /**
         * The amount to swap from the source mint in quarks.
         */
        val amount: Long,
        /**
         * Where "amount" of "from_mint" will be sent from to the VM swap PDA
         */
        val fundingSource: FundingSource,
        /**
         * The ID of the "transaction" to lookup funding state.
         *
         * For [FundingSource.SUBMIT_INTENT], this value is the base58 encoded intent ID.
         */
        val fundingId: String
    ): SwapStartKind
}