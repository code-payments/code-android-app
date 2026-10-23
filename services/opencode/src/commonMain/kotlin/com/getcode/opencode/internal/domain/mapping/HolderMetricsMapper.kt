package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.HolderMetrics
import javax.inject.Inject

internal class HolderMetricsMapper @Inject constructor() :
    Mapper<OcpCurrencyService.HolderMetrics, HolderMetrics> {
    override fun map(from: OcpCurrencyService.HolderMetrics): HolderMetrics {
        return HolderMetrics(
            currentHolders = from.currentHolders,
            holderDeltas = from.holderDeltasList.mapNotNull {
                HolderMetrics.HolderDelta(
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