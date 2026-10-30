package com.flipcash.app.core.tokens

import com.getcode.libs.parcelable.Parcelable
import com.getcode.libs.parcelable.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface CurrencyCreatorResult : Parcelable {
    @Parcelize
    @Serializable
    data class Success(
        /** The launch finished on the funding screen, never reaching Processing. */
        val completedInPlace: Boolean = false,
    ) : CurrencyCreatorResult

    @Parcelize
    @Serializable
    data object Canceled : CurrencyCreatorResult
}
