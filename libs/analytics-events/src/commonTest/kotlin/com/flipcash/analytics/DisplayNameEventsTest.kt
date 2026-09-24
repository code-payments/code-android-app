package com.flipcash.analytics

import com.flipcash.analytics.events.DisplayNameEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayNameEventsTest {

    @Test
    fun setCarriesEverySourceByItsWireString() {
        assertEquals(
            listOf(
                AnalyticsEvent("Display Name Set", mapOf("Source" to PropertyValue.Text("Onboarding"))),
                AnalyticsEvent("Display Name Set", mapOf("Source" to PropertyValue.Text("My Account"))),
                AnalyticsEvent("Display Name Set", mapOf("Source" to PropertyValue.Text("Tip Card Setup"))),
            ),
            listOf(DisplayNameSource.ONBOARDING, DisplayNameSource.MY_ACCOUNT, DisplayNameSource.TIP_CARD_SETUP)
                .map(DisplayNameEvents::set),
        )
    }

    @Test
    fun updatedCarriesTheSource() {
        assertEquals(
            AnalyticsEvent("Display Name Updated", mapOf("Source" to PropertyValue.Text("My Account"))),
            DisplayNameEvents.updated(DisplayNameSource.MY_ACCOUNT),
        )
    }
}
