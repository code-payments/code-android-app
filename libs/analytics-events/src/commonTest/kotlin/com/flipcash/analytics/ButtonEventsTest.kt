package com.flipcash.analytics

import com.flipcash.analytics.events.ButtonEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonEventsTest {

    @Test
    fun tappedNamesEveryButtonByItsWireString() {
        assertEquals(
            listOf(
                "Button: Create Account",
                "Button: Save Access Key",
                "Button: Wrote Access Key",
                "Button: Allow Push",
                "Button: Skip Push",
                "Button: Allow Contacts",
                "Button: Skip Contacts",
                "Button: Buy With Reserves",
                "Button: Buy With Phantom",
                "Button: Buy With Coinbase",
                "Button: Buy With Other Wallet",
                "Button: Share Token Info",
            ).map { AnalyticsEvent(it, emptyMap()) },
            Button.entries.map(ButtonEvents::tapped),
        )
    }
}
