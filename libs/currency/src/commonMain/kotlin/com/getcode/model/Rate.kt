package com.getcode.model

import com.getcode.libs.parcelable.Parcelable
import com.getcode.libs.parcelable.Parcelize
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