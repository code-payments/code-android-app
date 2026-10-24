package com.flipcash.app.core.withdrawal

import com.getcode.libs.parcelable.Parcelable
import com.getcode.libs.parcelable.Parcelize
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
