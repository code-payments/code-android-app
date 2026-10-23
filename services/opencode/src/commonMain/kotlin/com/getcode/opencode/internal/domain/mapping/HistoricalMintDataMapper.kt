package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.util.toInstantFromMillis
import javax.inject.Inject

internal class HistoricalMintDataMapper @Inject constructor(): Mapper<OcpCurrencyService.HistoricalMintData, HistoricalMintData> {
    override fun map(from: OcpCurrencyService.HistoricalMintData): HistoricalMintData {
        return HistoricalMintData(
            snapshotAt = (from.timestamp.seconds * 1000L).toInstantFromMillis(),
            marketCap = from.marketCap,
        )
    }
}