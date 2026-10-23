package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.MarketCapMetrics
import javax.inject.Inject

internal class MarketCapMetricsMapper @Inject constructor() :
    Mapper<OcpCurrencyService.MarketCapMetrics, MarketCapMetrics> {
    override fun map(from: OcpCurrencyService.MarketCapMetrics): MarketCapMetrics {
        return MarketCapMetrics(
            currentMarketCap = from.currentMarketCap,
            marketCapDeltas = from.marketCapDeltasList.mapNotNull {
                MarketCapMetrics.MarketCapDelta(
                    range = when (it.range) {
                        OcpCurrencyService.PredefinedRange.ALL_TIME -> WindowedRange.AllTime
                        OcpCurrencyService.PredefinedRange.LAST_DAY -> WindowedRange.LastDay
                        OcpCurrencyService.PredefinedRange.LAST_WEEK -> WindowedRange.LastWeek
                        OcpCurrencyService.PredefinedRange.LAST_MONTH -> WindowedRange.LastMonth
                        OcpCurrencyService.PredefinedRange.LAST_YEAR -> WindowedRange.LastYear
                        OcpCurrencyService.PredefinedRange.UNRECOGNIZED -> return@mapNotNull null
                    },
                    delta = it.delta
                )
            }
        )
    }
}
