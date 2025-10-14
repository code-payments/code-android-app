package com.flipcash.app.core.tokens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface TokenPurpose: Parcelable {
    data object Send : TokenPurpose
    data object Withdraw: TokenPurpose
    data object Deposit: TokenPurpose
    data object Balance : TokenPurpose
}