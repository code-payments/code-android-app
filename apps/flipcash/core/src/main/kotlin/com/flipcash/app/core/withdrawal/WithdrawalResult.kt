package com.flipcash.app.core.withdrawal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface WithdrawalResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : WithdrawalResult

    @Parcelize
    @Serializable
    data object Canceled : WithdrawalResult
}
