package com.flipcash.app.core.tipping

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface TipResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : TipResult

    @Parcelize
    @Serializable
    data object Canceled : TipResult
}
