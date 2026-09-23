package com.flipcash.analytics

import com.flipcash.analytics.events.SwapEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class SwapEventsTest {

    private val amount = Amount(fiat = 3.0, currency = "USD", usdc = 3.0, quarks = 300)
    private val properties = mapOf(
        "Mint" to PropertyValue.Text("mint"),
        "Fiat" to PropertyValue.Number(3.0),
        "Currency" to PropertyValue.Text("USD"),
        "USDC" to PropertyValue.Number(3.0),
        "Quarks" to PropertyValue.Number(300.0),
    )

    @Test
    fun purchaseIsNamedForItsMethod() {
        assertEquals(
            listOf(
                AnalyticsEvent("Token Purchase With Reserves", properties),
                AnalyticsEvent("Token Purchase With Phantom", properties),
                AnalyticsEvent("Token Purchase With Coinbase", properties),
            ),
            listOf(PurchaseMethod.RESERVES, PurchaseMethod.PHANTOM, PurchaseMethod.COINBASE)
                .map { SwapEvents.purchase(it, "mint", amount, null) },
        )
    }

    @Test
    fun purchaseCarriesTheError() {
        assertEquals(
            AnalyticsEvent("Token Purchase With Coinbase", properties + ("Error" to PropertyValue.Text("e"))),
            SwapEvents.purchase(PurchaseMethod.COINBASE, "mint", amount, "e"),
        )
    }

    @Test
    fun sellCarriesTheFeeAsANumber() {
        assertEquals(
            AnalyticsEvent(
                "Token Sell",
                properties + mapOf("Fee" to PropertyValue.Number(0.03), "Error" to PropertyValue.Text("e")),
            ),
            SwapEvents.sell("mint", amount, 0.03, "e"),
        )
    }
}
