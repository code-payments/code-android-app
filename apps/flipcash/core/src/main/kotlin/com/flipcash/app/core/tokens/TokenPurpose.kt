package com.flipcash.app.core.tokens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface TokenPurpose: Parcelable {
    @Serializable data object Select : TokenPurpose
    @Serializable data object Withdraw: TokenPurpose
    @Serializable data object Deposit: TokenPurpose
    @Serializable data object Balance : TokenPurpose
}