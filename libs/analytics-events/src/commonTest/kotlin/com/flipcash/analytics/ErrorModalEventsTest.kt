package com.flipcash.analytics

import com.flipcash.analytics.events.ErrorModalEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorModalEventsTest {

    @Test
    fun displayedCarriesEverythingItIsGiven() {
        assertEquals(
            AnalyticsEvent(
                "Error Modal Displayed",
                mapOf(
                    "Title" to PropertyValue.Text("t"),
                    "Message" to PropertyValue.Text("m"),
                    "Screen" to PropertyValue.Text("s"),
                    "Call Site" to PropertyValue.Text("c"),
                ),
            ),
            ErrorModalEvents.displayed("t", "m", "s", "c"),
        )
    }

    @Test
    fun displayedOmitsAbsentScreenAndCallSite() {
        assertEquals(
            AnalyticsEvent(
                "Error Modal Displayed",
                mapOf("Title" to PropertyValue.Text("t"), "Message" to PropertyValue.Text("m")),
            ),
            ErrorModalEvents.displayed("t", "m", null, null),
        )
    }
}
