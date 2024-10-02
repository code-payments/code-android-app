package com.getcode.network.exchange

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalExchange: ProvidableCompositionLocal<Exchange> = staticCompositionLocalOf { ExchangeNull() }

interface Exchange {
    val localRate: Rate
    fun observeLocalRate(): Flow<Rate>

    val entryRate: Rate
    fun observeEntryRate(): Flow<Rate>

    fun rates(): Map<CurrencyCode, Rate>
    fun observeRates(): Flow<Map<CurrencyCode, Rate>>

    suspend fun fetchRatesIfNeeded()

    fun rateFor(currencyCode: CurrencyCode): Rate?

    fun rateForUsd(): Rate?
}

class ExchangeNull : Exchange {
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

    override suspend fun fetchRatesIfNeeded() = Unit

    override fun rateFor(currencyCode: CurrencyCode): Rate? {
        return null
    }

    override fun rateForUsd(): Rate? {
        return null
    }

}