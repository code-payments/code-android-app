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
    @Serializable data class LaunchFunding(val amount: Fiat): TokenPurpose
    @Serializable data class Tip(val amount: Fiat?): TriggersChange
    @Serializable data object Withdraw: TokenPurpose
    @Serializable data object Deposit: TokenPurpose
    @Serializable data object Balance : TokenPurpose
}