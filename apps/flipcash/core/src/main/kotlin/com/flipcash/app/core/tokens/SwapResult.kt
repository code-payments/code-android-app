package com.flipcash.app.core.tokens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface SwapResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : SwapResult

    @Parcelize
    @Serializable
    data object Canceled : SwapResult
}
