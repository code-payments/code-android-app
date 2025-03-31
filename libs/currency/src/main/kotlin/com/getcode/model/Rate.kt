package com.getcode.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Rate(
    val fx: Double,
    val currency: CurrencyCode
): Parcelable {
    companion object {
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.KIN)
    }
}

fun Rate?.orOneToOne() = this ?: Rate.oneToOne