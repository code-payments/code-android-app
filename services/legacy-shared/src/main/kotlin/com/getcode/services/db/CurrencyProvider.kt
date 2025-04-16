package com.getcode.services.db

import com.getcode.model.Currency

interface CurrencyProvider {
    suspend fun preferredCurrency(): Currency?
    suspend fun defaultCurrency(): Currency?
    fun suffix(currency: Currency?): String
}