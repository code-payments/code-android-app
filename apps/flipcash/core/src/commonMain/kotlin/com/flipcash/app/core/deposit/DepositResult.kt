package com.flipcash.app.core.deposit

import com.getcode.libs.parcelable.Parcelable
import com.getcode.libs.parcelable.Parcelize
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
