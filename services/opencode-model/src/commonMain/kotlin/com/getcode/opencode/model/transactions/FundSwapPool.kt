package com.getcode.opencode.model.transactions

import com.getcode.opencode.internal.solana.extensions.deriveCoinbasePoolAddress
import com.getcode.opencode.internal.solana.model.CoinbaseStablecoinPoolAccount
import com.getcode.solana.keys.PublicKey

sealed interface FundSwapPool {
    data class Usdf(val pool: LiquidityPool) : FundSwapPool
    data class CoinbaseStableSwapper(val feeRecipient: PublicKey) : FundSwapPool {
        companion object {
            /** The on-chain address of the Coinbase liquidity pool account. */
            val poolAddress: PublicKey
                get() = PublicKey.deriveCoinbasePoolAddress().publicKey

            /**
             * Parses the on-chain pool account data and returns a [CoinbaseStableSwapper]
             * with the resolved fee recipient.
             */
            fun fromAccountData(data: ByteArray): CoinbaseStableSwapper {
                val poolAccount = CoinbaseStablecoinPoolAccount.fromAccountData(data)
                return CoinbaseStableSwapper(feeRecipient = poolAccount.feeRecipient)
            }
        }
    }
}