package com.flipcash.app.analytics

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PeopleCounter

/** Records what was sent, for tests. Not thread-safe; tests drive it from one dispatcher. */
class RecordingAnalytics : FlipcashAnalyticsService by StubFlipcashAnalytics() {
    val events = mutableListOf<AnalyticsEvent>()
    val increments = mutableListOf<Pair<PeopleCounter, Double>>()
    override fun track(event: AnalyticsEvent) { events += event }
    override fun increment(counter: PeopleCounter, amount: Double) { increments += counter to amount }
}
