package com.flipcash.app.core.tokens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface CurrencyCreatorResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : CurrencyCreatorResult

    @Parcelize
    @Serializable
    data object Canceled : CurrencyCreatorResult
}
