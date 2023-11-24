package com.getcode.model

data class Rate(
    val fx: Double,
    val currency: CurrencyCode
) {
    companion object {
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.KIN)
    }

}