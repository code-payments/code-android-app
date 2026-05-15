package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents the intent or specific action behind a token swap operation.
 *
 * This sealed interface defines the possible directions of a trade, carrying the necessary context
 * (such as the specific [Mint]) required to initialize or execute the swap.
 *
 * @see Buy Represents a purchase intent (e.g., swapping base currency for a specific token).
 * @see Sell Represents a liquidation intent (e.g., swapping a specific token back to base currency).
 */
@Serializable
@Parcelize
sealed interface SwapPurpose : Parcelable {
    sealed interface BalanceIncrease
    sealed interface BalanceDecrease
    @Serializable data class Buy(val mint: Mint) : SwapPurpose, BalanceIncrease
    @Serializable data class FundWithWallet(val mint: Mint): SwapPurpose, BalanceIncrease
    @Serializable data class Sell(val mint: Mint) : SwapPurpose, BalanceDecrease
}
