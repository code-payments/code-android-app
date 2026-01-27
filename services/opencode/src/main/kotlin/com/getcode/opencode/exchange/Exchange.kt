package com.getcode.opencode.exchange

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Signature
import kotlinx.coroutines.flow.Flow

interface Exchange {
    val entryRate: Rate
    fun observeEntryRate(): Flow<Rate>
    suspend fun setPreferredEntryCurrency(currencyCode: CurrencyCode)
    val balanceRate: Rate
    fun observeBalanceRate(): Flow<Rate>
    suspend fun setPreferredBalanceCurrency(currencyCode: CurrencyCode)

    fun updateUserMints(mints: List<Mint>)

    fun rates(): Map<CurrencyCode, Rate>
    fun observeRates(): Flow<Map<CurrencyCode, Rate>>

    suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate> = rates()): List<Currency>
    fun getCurrency(code: String): Currency?
    fun getCurrencyWithRate(code: String, rate: Double): Currency?
    fun getFlagByCurrency(currencyCode: String?): Int?
    fun getFlag(countryCode: String): Int?

    fun rateFor(currencyCode: CurrencyCode): Rate?
    fun proofFor(currencyCode: CurrencyCode): Signature?

    fun rateForUsd(): Rate
    fun proofForUsd(): Signature?
    fun rateToUsd(from: CurrencyCode): Rate?
}