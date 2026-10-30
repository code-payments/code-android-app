package com.flipcash.analytics

import com.flipcash.analytics.events.CurrencyLaunchEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyLaunchEventsTest {

    private val properties = mapOf(
        "State" to PropertyValue.Text("Success"),
        "Mint" to PropertyValue.Text("mint"),
        "Payment Mint" to PropertyValue.Text("payment"),
        "Fiat" to PropertyValue.Number(25.0),
        "Currency" to PropertyValue.Text("USD"),
    )

    @Test
    fun launchIsNamedForItsMethod() {
        assertEquals(
            listOf(
                AnalyticsEvent("Currency Launch With Reserves", properties),
                AnalyticsEvent("Currency Launch With Currency", properties),
            ),
            listOf(LaunchMethod.RESERVES, LaunchMethod.CURRENCY).map {
                CurrencyLaunchEvents.launched(it, State.SUCCESS, "mint", "payment", 25.0, "USD", null)
            },
        )
    }

    @Test
    fun launchCarriesTheErrorOnFailure() {
        assertEquals(
            AnalyticsEvent(
                "Currency Launch With Currency",
                properties + mapOf(
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("e"),
                ),
            ),
            CurrencyLaunchEvents.launched(LaunchMethod.CURRENCY, State.FAILURE, "mint", "payment", 25.0, "USD", "e"),
        )
    }
}
