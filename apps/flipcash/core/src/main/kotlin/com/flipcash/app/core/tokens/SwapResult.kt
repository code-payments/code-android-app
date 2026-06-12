package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.opencode.model.financial.Fiat
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface SwapResult : Parcelable {
    @Parcelize
    @Serializable
    data class Success(val amount: Fiat = Fiat.Zero) : SwapResult

    @Parcelize
    @Serializable
    data object Canceled : SwapResult

    @Parcelize
    @Serializable
    data object OpenDeposit : SwapResult
}
