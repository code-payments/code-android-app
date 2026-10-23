package com.flipcash.app.core.tokens

import com.getcode.libs.parcelable.Parcelable
import com.getcode.opencode.model.financial.Fiat
import com.getcode.libs.parcelable.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface SwapResult : Parcelable {
    @Parcelize
    @Serializable
    data class Success(
        val amount: Fiat = Fiat.Zero,
        /** The transaction finished on the confirm screen, never reaching Processing. */
        val completedInPlace: Boolean = false,
    ) : SwapResult

    @Parcelize
    @Serializable
    data object Canceled : SwapResult

    @Parcelize
    @Serializable
    data object OpenDeposit : SwapResult
}
