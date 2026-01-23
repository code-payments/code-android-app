package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import kotlin.time.Instant

internal class InternalCurrencyRepository @Inject constructor(
    private val service: CurrencyService,
) : CurrencyRepository {
    override suspend fun getRates(from: Instant?): Result<Map<CurrencyCode, Rate>> =
        service.getRates(from)

    override suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>> =
        service.getMints(addresses)

    override suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange
    ): Result<List<HistoricalMintData>> =
        service.getHistoricalMintData(mint, currencyCode, windowedRange)
}