package com.flipcash.analytics

import com.flipcash.analytics.events.AccountEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountEventsTest {

    @Test
    fun createAccountPaymentCarriesPriceCurrencyAndOwner() {
        assertEquals(
            AnalyticsEvent(
                "Create Account Payment",
                mapOf(
                    "Fiat" to PropertyValue.Number(4.99),
                    "Currency" to PropertyValue.Text("USD"),
                    "Owner Public Key" to PropertyValue.Text("owner"),
                ),
            ),
            AccountEvents.createAccountPayment(4.99, "USD", "owner"),
        )
    }

    @Test
    fun propertylessEvents() {
        assertEquals(
            listOf(
                AnalyticsEvent("Entered Phone Number", emptyMap()),
                AnalyticsEvent("Verified Phone Number", emptyMap()),
                AnalyticsEvent("Linked Phone Number", emptyMap()),
                AnalyticsEvent("Complete Onboarding", emptyMap()),
            ),
            listOf(
                AccountEvents.enteredPhoneNumber(),
                AccountEvents.verifiedPhoneNumber(),
                AccountEvents.linkedPhoneNumber(),
                AccountEvents.completeOnboarding(),
            ),
        )
    }
}
