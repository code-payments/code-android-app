package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface TokenPurpose: Parcelable {
    @Serializable data object Select : TokenPurpose
    @Serializable data class Purchase(val desiredToken: Mint, val amount: Fiat) : TokenPurpose
    @Serializable data object Withdraw: TokenPurpose
    @Serializable data object Deposit: TokenPurpose
    @Serializable data object Balance : TokenPurpose
}