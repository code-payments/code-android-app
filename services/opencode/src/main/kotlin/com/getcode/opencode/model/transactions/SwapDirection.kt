package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdc

sealed interface SwapDirection {
    // USDC -> Bonded Token
    data class Buy(val mint: MintMetadata): SwapDirection
    // Bonded Token -> USDC
    data class Sell(val mint: MintMetadata): SwapDirection

    val sourceMint: MintMetadata
        get() = when (this) {
            is Buy -> MintMetadata.usdc
            is Sell -> mint
        }

    val destinationMint: MintMetadata
        get() = when (this) {
            is Buy -> mint
            is Sell -> MintMetadata.usdc
        }
}