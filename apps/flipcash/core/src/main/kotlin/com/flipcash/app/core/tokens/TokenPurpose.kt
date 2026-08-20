package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface TokenPurpose: Parcelable {

    @Parcelize
    sealed interface TriggersChange: TokenPurpose

    @Serializable data object Select : TriggersChange {

    }
    @Serializable data class Swap(val desiredToken: Mint, val amount: Fiat) : TokenPurpose

    /**
     * Picks the currency a Convert lands in. [source] is the currency being spent (excluded from
     * the list); [current] is the destination already chosen, shown with a checkmark.
     */
    @Serializable data class ConvertDestination(val source: Mint, val current: Mint) : TokenPurpose

    /**
     * Picks the currency a Get is funded from. [target] is the currency being bought (excluded from
     * the list, since the server rejects same-mint swaps); [current] is the source already chosen,
     * shown with a checkmark.
     */
    @Serializable data class BuyFunding(val target: Mint, val current: Mint) : TokenPurpose
    @Serializable data class LaunchFunding(val amount: Fiat): TokenPurpose
    @Serializable data class Tip(val amount: Fiat?): TriggersChange
    @Serializable data object Withdraw: TokenPurpose
    @Serializable data object Deposit: TokenPurpose
    @Serializable data object Balance : TokenPurpose
}