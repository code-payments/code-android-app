package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.HolderMetrics
import javax.inject.Inject

internal class HolderMetricsMapper @Inject constructor() :
    Mapper<CurrencyService.HolderMetrics, HolderMetrics> {
    override fun map(from: CurrencyService.HolderMetrics): HolderMetrics {
        return HolderMetrics(
            currentHolders = from.currentHolders,
            holderDeltas = from.holderDeltasList.mapNotNull {
                HolderMetrics.HolderDelta(
                    range = when (it.range) {
                        CurrencyService.PredefinedRange.ALL_TIME -> WindowedRange.AllTime
                        CurrencyService.PredefinedRange.LAST_DAY -> WindowedRange.LastDay
                        CurrencyService.PredefinedRange.LAST_WEEK -> WindowedRange.LastWeek
                        CurrencyService.PredefinedRange.LAST_MONTH -> WindowedRange.LastMonth
                        CurrencyService.PredefinedRange.LAST_YEAR -> WindowedRange.LastYear
                        CurrencyService.PredefinedRange.UNRECOGNIZED -> return@mapNotNull null
                    },
                    delta = it.delta
                )
            }
        )
    }
}