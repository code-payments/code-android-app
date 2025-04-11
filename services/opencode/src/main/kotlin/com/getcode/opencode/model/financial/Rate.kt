package com.getcode.opencode.model.financial

import kotlinx.serialization.Serializable

@Serializable
data class Rate(
    val fx: Double,
    val currency: CurrencyCode
) {
    companion object {
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.USD)
    }
}

fun Rate?.orOneToOne() = this ?: Rate.oneToOne