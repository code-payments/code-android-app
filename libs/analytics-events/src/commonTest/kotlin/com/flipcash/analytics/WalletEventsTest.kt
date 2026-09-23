package com.flipcash.analytics

import com.flipcash.analytics.events.WalletEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletEventsTest {

    private val phantom = mapOf("Provider" to PropertyValue.Text("Phantom"))

    @Test
    fun providerOnlyEvents() {
        assertEquals(AnalyticsEvent("Wallet: Connect", phantom), WalletEvents.connect(WalletProvider.PHANTOM))
        assertEquals(
            AnalyticsEvent("Wallet: Transactions Failed", phantom),
            WalletEvents.transactionsFailed(WalletProvider.PHANTOM),
        )
        assertEquals(AnalyticsEvent("Wallet: Cancel", phantom), WalletEvents.cancel(WalletProvider.PHANTOM))
    }

    @Test
    fun requestAmountCarriesProviderAndAmount() {
        assertEquals(
            AnalyticsEvent(
                "Wallet: Request Amount",
                phantom + mapOf(
                    "Fiat" to PropertyValue.Number(10.0),
                    "Currency" to PropertyValue.Text("USD"),
                    "USDC" to PropertyValue.Number(10.0),
                    "Quarks" to PropertyValue.Number(1_000_000.0),
                ),
            ),
            WalletEvents.requestAmount(
                WalletProvider.PHANTOM,
                Amount(fiat = 10.0, currency = "USD", usdc = 10.0, quarks = 1_000_000),
            ),
        )
    }
}
