package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.PublicKey

sealed interface StartSwapServerParameters {
    data class CurrencyCreator(
        val nonce: PublicKey,
        val blockHash: PublicKey,
    ): StartSwapServerParameters
}