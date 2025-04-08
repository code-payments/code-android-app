package com.getcode.opencode.exchange

import com.getcode.opencode.model.core.Currency
import com.getcode.opencode.model.core.CurrencyCode
import com.getcode.opencode.model.core.Rate
import kotlinx.coroutines.flow.Flow

interface Exchange {
    val localRate: Rate
    fun observeLocalRate(): Flow<Rate>

    val entryRate: Rate
    fun observeEntryRate(): Flow<Rate>

    fun rates(): Map<CurrencyCode, Rate>
    fun observeRates(): Flow<Map<CurrencyCode, Rate>>

    suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency>
    fun getCurrency(code: String): Currency?
    fun getCurrencyWithRate(code: String, rate: Double): Currency?
    fun getFlagByCurrency(currencyCode: String?): Int?
    fun getFlag(countryCode: String): Int?

    suspend fun fetchRatesIfNeeded()

    fun rateFor(currencyCode: CurrencyCode): Rate?

    fun rateForUsd(): Rate?
}