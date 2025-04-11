package com.getcode.opencode.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalExchange: ProvidableCompositionLocal<Exchange> = staticCompositionLocalOf { ExchangeNull() }

private class ExchangeNull : Exchange {
    override val localRate: Rate
        get() = Rate.oneToOne

    override val entryRate: Rate
        get() = Rate.oneToOne


    override fun observeLocalRate(): Flow<Rate> {
        return emptyFlow()
    }

    override fun observeEntryRate(): Flow<Rate> {
        return emptyFlow()
    }

    override fun rates(): Map<CurrencyCode, Rate> {
        return emptyMap()
    }

    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> {
        return emptyFlow()
    }

    override suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency> {
        return emptyList()
    }

    override fun getCurrency(code: String): Currency? {
        return null
    }

    override fun getCurrencyWithRate(code: String, rate: Double): Currency? {
        return null
    }

    override fun getFlagByCurrency(currencyCode: String?): Int? {
        return null
    }

    override fun getFlag(countryCode: String): Int? {
        return null
    }

    override suspend fun fetchRatesIfNeeded() = Unit

    override fun rateFor(currencyCode: CurrencyCode): Rate? {
        return null
    }

    override fun rateForUsd(): Rate? {
        return null
    }
}