package com.flipcash.analytics

import com.flipcash.analytics.events.PaymentRequestEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentRequestEventsTest {

    private val amount = Amount(fiat = 5.0, currency = "USD", usdc = 5.0, quarks = 500, exchangeRate = 1.0, mint = "mint")

    @Test
    fun createdCarriesPayerCountAndOutcome() {
        assertEquals(
            AnalyticsEvent(
                "Payment Request: Created",
                mapOf(
                    "Payer Count" to PropertyValue.Number(4.0),
                    "State" to PropertyValue.Text("Success"),
                    "Fiat" to PropertyValue.Number(5.0),
                    "Currency" to PropertyValue.Text("USD"),
                    "USDC" to PropertyValue.Number(5.0),
                    "Quarks" to PropertyValue.Number(500.0),
                    "Exchange Rate" to PropertyValue.Number(1.0),
                    "Mint" to PropertyValue.Text("mint"),
                ),
            ),
            PaymentRequestEvents.created(4, State.SUCCESS, amount, null),
        )
    }

    @Test
    fun createdFailureCarriesTheError() {
        assertEquals(
            AnalyticsEvent(
                "Payment Request: Created",
                mapOf(
                    "Payer Count" to PropertyValue.Number(2.0),
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("Denied"),
                ),
            ),
            PaymentRequestEvents.created(2, State.FAILURE, null, "Denied"),
        )
    }

    @Test
    fun closedCarriesStateAndError() {
        assertEquals(
            AnalyticsEvent("Payment Request: Closed", mapOf("State" to PropertyValue.Text("Success"))),
            PaymentRequestEvents.closed(State.SUCCESS, null),
        )
        assertEquals(
            AnalyticsEvent(
                "Payment Request: Closed",
                mapOf("State" to PropertyValue.Text("Failure"), "Error" to PropertyValue.Text("Network")),
            ),
            PaymentRequestEvents.closed(State.FAILURE, "Network"),
        )
    }
}
