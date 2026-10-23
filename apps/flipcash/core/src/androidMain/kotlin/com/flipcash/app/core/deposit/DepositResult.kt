package com.flipcash.app.core.deposit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface DepositResult : Parcelable {
    @Parcelize
    @Serializable
    data object Success : DepositResult

    @Parcelize
    @Serializable
    data object Canceled : DepositResult
}
