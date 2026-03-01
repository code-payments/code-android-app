package com.getcode.opencode.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.VerifiedResponseData
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Signature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

val LocalExchange: ProvidableCompositionLocal<Exchange> = staticCompositionLocalOf { ExchangeNull() }

private class ExchangeNull : Exchange {
    override val balanceRate: Rate
        get() = Rate.oneToOne

    override val entryRate: Rate
        get() = Rate.oneToOne


    override fun observeEntryRate(): Flow<Rate> {
        return emptyFlow()
    }

    override suspend fun setPreferredEntryCurrency(currencyCode: CurrencyCode) {

    }

    override fun observeBalanceRate(): Flow<Rate> {
        return emptyFlow()
    }

    override suspend fun setPreferredBalanceCurrency(currencyCode: CurrencyCode) {

    }

    override fun updateUserMints(mints: List<Mint>) = Unit

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

    override fun rateFor(currencyCode: CurrencyCode): Rate? {
        return null
    }

    override fun rateForUsd(): Rate {
        return Rate.oneToOne
    }

    override fun rateToUsd(from: CurrencyCode): Rate? = null
}