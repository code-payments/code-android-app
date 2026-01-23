package com.getcode.opencode.repositories

import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlinx.datetime.Instant

interface CurrencyRepository {
    suspend fun getRates(from: Instant?): Result<Map<CurrencyCode, Rate>>
    suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>>

    suspend fun getHistoricalMintData(mint: Mint, currencyCode: CurrencyCode, windowedRange: WindowedRange): Result<List<HistoricalMintData>>
}