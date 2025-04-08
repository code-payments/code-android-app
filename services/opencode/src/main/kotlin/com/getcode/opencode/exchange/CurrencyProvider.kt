package com.getcode.opencode.exchange

import com.getcode.opencode.model.core.Currency

interface CurrencyProvider {
    suspend fun preferredCurrency(): Currency?
    suspend fun defaultCurrency(): Currency?
    fun suffix(currency: Currency?): String
}

