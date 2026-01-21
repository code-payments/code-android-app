package com.getcode.opencode.model.transactions

import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

sealed class SwapFundingSource {

    data object Unknown: SwapFundingSource()

    /**
     * Swap to be funded via a buy/sell/swap within Flipcash via submitIntent.
     * @param id The intent ID.
     */
    data class SubmitIntent(val id: PublicKey = PublicKey.generate()): SwapFundingSource()

    /**
     * Represents a funding source where the user pays for the transaction
     * using an external wallet, such as Phantom. This is typically used
     * for swaps where the user provides the necessary USDC from their own
     * wallet to complete the transaction.
     */
    data class ExternalWallet(val transactionSignature: PublicKey): SwapFundingSource()
}