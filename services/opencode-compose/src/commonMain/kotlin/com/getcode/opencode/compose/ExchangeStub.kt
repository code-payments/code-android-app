package com.getcode.opencode.compose

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * An [Exchange] with fixed rates for previews and tests. It resolves no flags; on Android the
 * `ExchangeStub(providedRates, context)` function returns one that does.
 */
open class ExchangeStub(
    private val providedRates: Map<CurrencyCode, Rate> = emptyMap(),
) : Exchange {
    override val preferredRate: Rate = Rate.oneToOne

    override fun observePreferredRate(): Flow<Rate> = emptyFlow()

    override suspend fun setPreferredCurrency(currencyCode: CurrencyCode) = Unit
    override fun updateUserMints(mints: List<Mint>) = Unit

    override fun rates(): Map<CurrencyCode, Rate> = providedRates

    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> = flowOf(providedRates)

    override suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency> {
        return withContext(Dispatchers.Default) {
            return@withContext CurrencyCode.entries
                .mapNotNull { code ->
                    val rate = rates[code]?.fx ?: 0.0
                    getCurrencyWithRate(code.name, rate)
                }
        }
    }

    override fun getCurrency(code: String): Currency? = null

    override fun getCurrencyWithRate(
        code: String,
        rate: Double
    ): Currency? = null

    override fun getFlagByCurrency(currencyCode: String?): Int? = null

    override fun getFlag(countryCode: String): Int? = null

    override fun rateFor(currencyCode: CurrencyCode): Rate? {
        return providedRates[currencyCode]
    }

    override fun rateForUsd(): Rate {
        return providedRates[CurrencyCode.USD]!!
    }

    override fun rateToUsd(from: CurrencyCode): Rate? {
        val fromRate = rateFor(from) ?: return null

        return Rate(
            fx = 1 / fromRate.fx,
            currency = CurrencyCode.USD
        )
    }
}
