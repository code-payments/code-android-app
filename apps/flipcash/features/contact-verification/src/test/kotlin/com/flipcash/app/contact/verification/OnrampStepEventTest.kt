package com.flipcash.app.contact.verification

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.OnrampStep
import com.flipcash.analytics.events.OnrampEvents
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The five verification screens track [OnrampEvents.step] straight from a `LaunchedEffect`. These
 * pin each step to the event name those screens sent before the shared builders, with no
 * properties, so the move changes nothing Mixpanel receives.
 */
class OnrampStepEventTest {

    @Test
    fun `each verification step keeps its event name`() {
        assertEquals(
            listOf(
                AnalyticsEvent("Onramp: Show Verification Info"),
                AnalyticsEvent("Onramp: Show Enter Phone"),
                AnalyticsEvent("Onramp: Show Confirm Phone"),
                AnalyticsEvent("Onramp: Show Enter Email"),
                AnalyticsEvent("Onramp: Show Confirm Email"),
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
