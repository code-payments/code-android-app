package com.flipcash.analytics

import com.flipcash.analytics.events.OnrampEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class OnrampEventsTest {

    @Test
    fun eachStepIsItsOwnPropertylessEvent() {
        assertEquals(
            listOf(
                AnalyticsEvent("Onramp: Show Verification Info", emptyMap()),
                AnalyticsEvent("Onramp: Show Enter Phone", emptyMap()),
                AnalyticsEvent("Onramp: Show Confirm Phone", emptyMap()),
                AnalyticsEvent("Onramp: Show Enter Email", emptyMap()),
                AnalyticsEvent("Onramp: Show Confirm Email", emptyMap()),
            ),
            listOf(
                OnrampStep.SHOW_INFO,
                OnrampStep.ENTER_PHONE,
                OnrampStep.CONFIRM_PHONE,
                OnrampStep.ENTER_EMAIL,
                OnrampStep.CONFIRM_EMAIL,
            ).map(OnrampEvents::step),
        )
    }
}
