package com.getcode.opencode.exchange

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface Exchange {
    val entryRate: Rate
    fun observeEntryRate(): Flow<Rate>
    suspend fun setPreferredEntryCurrency(currencyCode: CurrencyCode)
    val balanceRate: Rate
    fun observeBalanceRate(): Flow<Rate>
    suspend fun setPreferredBalanceCurrency(currencyCode: CurrencyCode)

    fun rates(): Map<CurrencyCode, Rate>
    fun observeRates(): Flow<Map<CurrencyCode, Rate>>

    val staleThreshold: Duration

    suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate> = rates()): List<Currency>
    fun getCurrency(code: String): Currency?
    fun getCurrencyWithRate(code: String, rate: Double): Currency?
    fun getFlagByCurrency(currencyCode: String?): Int?
    fun getFlag(countryCode: String): Int?

    suspend fun fetchRatesIfNeeded()

    fun rateFor(currencyCode: CurrencyCode): Rate?

    fun rateForUsd(): Rate
    fun rateToUsd(from: CurrencyCode): Rate?
}