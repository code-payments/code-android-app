package com.getcode.model

interface Value

data class Fiat(
    val currency: CurrencyCode,
    val amount: Double,
): Value