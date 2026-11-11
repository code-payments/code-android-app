package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf

sealed interface SwapRoute {
    // USDC -> Bonded Token
    data class Buy(val mint: MintMetadata): SwapRoute
    // Bonded Token -> USDC
    data class Sell(val mint: MintMetadata): SwapRoute
    // USDF -> USDC
    data object WithdrawUsdc: SwapRoute
    // Arbitrary currency -> currency (neither side the core mint)
    data class CrossCurrency(val from: MintMetadata, val to: MintMetadata): SwapRoute

    val sourceMint: MintMetadata
        get() = when (this) {
            is Buy -> MintMetadata.usdf
            is Sell -> mint
            is WithdrawUsdc -> MintMetadata.usdf
            is CrossCurrency -> from
        }

    val destinationMint: MintMetadata
        get() = when (this) {
            is Buy -> mint
            is Sell -> MintMetadata.usdf
            is WithdrawUsdc -> MintMetadata.usdc
            is CrossCurrency -> to
        }
}
