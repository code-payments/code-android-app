package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdf

sealed interface SwapDirection {
    // USDC -> Bonded Token
    data class Buy(val mint: MintMetadata): SwapDirection
    // Bonded Token -> USDC
    data class Sell(val mint: MintMetadata): SwapDirection

    val sourceMint: MintMetadata
        get() = when (this) {
            is Buy -> MintMetadata.usdf
            is Sell -> mint
        }

    val destinationMint: MintMetadata
        get() = when (this) {
            is Buy -> mint
            is Sell -> MintMetadata.usdf
        }
}