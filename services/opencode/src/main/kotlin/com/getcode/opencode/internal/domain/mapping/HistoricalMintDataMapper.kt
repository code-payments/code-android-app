package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.util.toInstantFromMillis
import javax.inject.Inject

internal class HistoricalMintDataMapper @Inject constructor(): Mapper<CurrencyService.HistoricalMintData, HistoricalMintData> {
    override fun map(from: CurrencyService.HistoricalMintData): HistoricalMintData {
        return HistoricalMintData(
            snapshotAt = (from.timestamp.seconds * 1000L).toInstantFromMillis(),
            marketCap = from.marketCap,
        )
    }
}