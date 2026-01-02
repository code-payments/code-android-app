package com.getcode.opencode.model.financial

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
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.USD)
        val ignore = Rate(fx = Double.MIN_VALUE, currency = CurrencyCode.USD)
    }

    fun isUsable() = this != ignore
}

fun Rate?.orOneToOne() = this ?: Rate.oneToOne