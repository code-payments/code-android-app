package com.getcode.opencode.controllers

import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyController @Inject constructor(
    private val repository: CurrencyRepository,
) {
    suspend fun getRates(
        from: Instant?
    ): Result<Map<CurrencyCode, Rate>> {
        return repository.getRates(from)
    }

    suspend fun getMintMetadata(
        addresses: List<Mint>
    ): Result<List<MintMetadata>> {
        return repository.getMintMetadata(addresses)
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> = runCatching {
        return repository.getHistoricalMintData(mint, currencyCode, windowedRange)
    }
}