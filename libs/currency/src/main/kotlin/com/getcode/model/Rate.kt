package com.getcode.model

import kotlinx.serialization.Serializable

@Serializable
data class Rate(
    val fx: Double,
    val currency: CurrencyCode
) {
    companion object {
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.KIN)
    }
}

fun Rate?.orOneToOne() = this ?: Rate.oneToOne