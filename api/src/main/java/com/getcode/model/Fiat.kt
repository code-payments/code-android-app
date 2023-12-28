package com.getcode.model

sealed interface Value

data class Fiat(
    val currency: CurrencyCode,
    val amount: Double,
): Value