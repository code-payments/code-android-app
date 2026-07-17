package com.getcode.opencode.model.transactions

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.plus
import com.getcode.solana.keys.PublicKey

data class StatefulSwapRequest(
    val owner: AccountCluster,
    val swapAuthority: KeyPair,
    val program: SwapProgram,
    val route: SwapRoute,
    val swapAmount: LocalFiat,
    val feeAmount: LocalFiat?,
    val swapId: SwapId,
    val verifiedState: VerifiedState,
    /**
     * Verified exchange data over the full amount (swap + fee), keyed to the source mint. Required
     * by the server when initializing a new reserve currency from a source mint other than the core
     * mint (the treasury-funded flow); null otherwise.
     */
    val fullAmountExchangeData: ExchangeData.Verified? = null,
) {
    val fundingIntentId: List<Byte>
        get() = when (program) {
            is SwapProgram.Reserve -> program.fundingIntentId
            is SwapProgram.Stablecoin -> program.fundingIntentId
        }

    val totalTransferAmount: LocalFiat
        get() {
            val fee = feeAmount ?: LocalFiat.Zero
            return swapAmount + fee
        }
}

sealed interface SwapProgram {
    /**
     * Server parameters for starting swaps against the Reserve program
     */
    data class Reserve(
        /**
         * The source mint that will be swapped from
         */
        val fromMint: PublicKey,
        /**
         * The destination mint that will be swapped from
         */
        val toMint: PublicKey,
        /**
         * Where "amount" of "from_mint" will be sent from to the VM swap PDA
         */
        val fundingSource: SwapFundingSource,
    ) : SwapProgram {
        val fundingIntentId: List<Byte>
            get() = when (fundingSource) {
                is SwapFundingSource.ExternalWallet -> fundingSource.transactionSignature
                is SwapFundingSource.SubmitIntent -> fundingSource.id
                is SwapFundingSource.CoinbaseOnramp -> throw IllegalArgumentException(
                    "Coinbase onramp does not use a funding intent"
                )
                SwapFundingSource.Unknown -> throw IllegalArgumentException("Invalid funding source")
            }
    }

    data class Stablecoin(
        /**
         * The source mint that will be swapped from
         */
        val fromMint: PublicKey,
        /**
         * The destination mint that will be swapped from
         */
        val toMint: PublicKey,
        /**
         * The destination owner account where from_mint tokens will land. Use
         * CanWithdrawToAccountResponse to determine if an account is an owner.
         */
        val destinationOwner: PublicKey,
        /**
         * Where "amount" of "from_mint" will be sent from to the VM swap PDA
         */
        val fundingSource: SwapFundingSource.SubmitIntent,
    ): SwapProgram {
        val fundingIntentId: List<Byte> = fundingSource.id
    }
}