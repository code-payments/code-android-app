package com.flipcash.app.analytics

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PeopleCounter

/** Sends shared analytics events. Every name and key comes from `:libs:analytics-events`. */
interface FlipcashAnalytics {
    fun track(event: AnalyticsEvent)

    /**
     * Adds to a cumulative people property. It can't be undone and carries no event identity,
     * so every caller must sit behind a watermark (see `EventStreamDelegate`).
     */
    fun increment(counter: PeopleCounter, amount: Double = 1.0)

    companion object {
        val None: FlipcashAnalytics = object : FlipcashAnalytics {
            override fun track(event: AnalyticsEvent) = Unit
            override fun increment(counter: PeopleCounter, amount: Double) = Unit
        }
    }
}
