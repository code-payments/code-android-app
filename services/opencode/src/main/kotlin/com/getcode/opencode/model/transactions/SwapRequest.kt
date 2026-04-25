package com.getcode.opencode.model.transactions

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.PublicKey

data class SwapRequest(
    val owner: AccountCluster,
    val swapAuthority: KeyPair,
    val kind: SwapStartKind,
    val direction: SwapDirection,
    val swapAmount: LocalFiat,
    val feeAmount: LocalFiat?,
    val swapId: SwapId,
    val verifiedState: VerifiedState,
) {
    val fundingIntentId = when (kind) {
        is SwapStartKind.Reserve -> kind.fundingIntentId
    }

    val totalTransferAmount: LocalFiat
        get() {
            val fee = feeAmount ?: LocalFiat(usdf = 0.toFiat(swapAmount.nativeAmount.currencyCode))
            return swapAmount + fee
        }
}

sealed interface SwapStartKind {
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
    ) : SwapStartKind {
        val fundingIntentId: List<Byte>
            get() = when (fundingSource) {
                is SwapFundingSource.ExternalWallet -> fundingSource.transactionSignature
                is SwapFundingSource.SubmitIntent -> fundingSource.id
                SwapFundingSource.Unknown -> throw IllegalArgumentException("Invalid funding source")
            }
    }
}