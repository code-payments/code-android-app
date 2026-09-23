package com.flipcash.analytics

import com.flipcash.analytics.events.DeeplinkEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class DeeplinkEventsTest {

    @Test
    fun openCarriesTheUrl() {
        assertEquals(
            AnalyticsEvent("Deeplink: Open", mapOf("URL" to PropertyValue.Text("https://app.flipcash.com/c"))),
            DeeplinkEvents.open("https://app.flipcash.com/c"),
        )
    }

    @Test
    fun parsedCarriesTheType() {
        assertEquals(
            AnalyticsEvent("Deeplink: Parse", mapOf("Type" to PropertyValue.Text("CashLink"))),
            DeeplinkEvents.parsed("CashLink"),
        )
    }

    @Test
    fun parseFailedCarriesTheUrlInTheError() {
        assertEquals(
            AnalyticsEvent(
                "Deeplink: Parse",
                mapOf("Error" to PropertyValue.Text("Failed to parse deeplink => https://example.com/x")),
            ),
            DeeplinkEvents.parseFailed("https://example.com/x"),
        )
    }

    @Test
    fun routedCarriesTypeAndError() {
        assertEquals(
            AnalyticsEvent("Deeplink: Routed", mapOf("Type" to PropertyValue.Text("CashLink"))),
            DeeplinkEvents.routed("CashLink", null),
        )
        assertEquals(
            AnalyticsEvent(
                "Deeplink: Routed",
                mapOf("Type" to PropertyValue.Text("CashLink"), "Error" to PropertyValue.Text("e")),
            ),
            DeeplinkEvents.routed("CashLink", "e"),
        )
    }
}
