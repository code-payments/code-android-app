package com.flipcash.analytics

import com.flipcash.analytics.events.AddMoneyEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class AddMoneyEventsTest {

    private val amount = Amount(fiat = 20.0, currency = "USD", usdc = 20.0, quarks = 2_000)
    private val amountProperties = mapOf(
        "Fiat" to PropertyValue.Number(20.0),
        "Currency" to PropertyValue.Text("USD"),
        "USDC" to PropertyValue.Number(20.0),
        "Quarks" to PropertyValue.Number(2_000.0),
    )

    @Test
    fun openedCarriesEverySourceByItsWireString() {
        assertEquals(
            listOf("Menu", "Give Shortfall", "Buy Shortfall", "Username Shortfall", "Chat", "Scanner", "Balance"),
            AddMoneySource.entries.map {
                val event = AddMoneyEvents.opened(it)
                assertEquals("Add Money: Opened", event.name)
                (event.properties.getValue("Source") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun methodSelectedCarriesEveryMethodByItsWireString() {
        assertEquals(
            listOf("Coinbase", "Phantom", "Other Wallet", "Reserves"),
            AddMoneyMethod.entries.map {
                val event = AddMoneyEvents.methodSelected(it)
                assertEquals(AnalyticsEvent("Add Money: Method Selected", mapOf("Method" to event.properties.getValue("Method"))), event)
                (event.properties.getValue("Method") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun amountConfirmedAndPaymentInvokedCarryMethodAndAmount() {
        val expected = mapOf("Method" to PropertyValue.Text("Phantom")) + amountProperties
        assertEquals(
            AnalyticsEvent("Add Money: Amount Confirmed", expected),
            AddMoneyEvents.amountConfirmed(AddMoneyMethod.PHANTOM, amount),
        )
        assertEquals(
            AnalyticsEvent("Add Money: Payment Invoked", expected),
            AddMoneyEvents.paymentInvoked(AddMoneyMethod.PHANTOM, amount),
        )
    }

    @Test
    fun addressCopiedCarriesTheMint() {
        assertEquals(
            AnalyticsEvent("Add Money: Address Copied", mapOf("Mint" to PropertyValue.Text("mint"))),
            AddMoneyEvents.addressCopied("mint"),
        )
    }

    @Test
    fun resultIsTheEventNamedAddMoney() {
        assertEquals(
            AnalyticsEvent(
                "Add Money",
                mapOf("Method" to PropertyValue.Text("Coinbase"), "State" to PropertyValue.Text("Success")) +
                    amountProperties,
            ),
            AddMoneyEvents.result(AddMoneyMethod.COINBASE, State.SUCCESS, amount, null),
        )
        assertEquals(
            AnalyticsEvent(
                "Add Money",
                mapOf(
                    "Method" to PropertyValue.Text("Other Wallet"),
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("declined"),
                ),
            ),
            AddMoneyEvents.result(AddMoneyMethod.OTHER_WALLET, State.FAILURE, null, "declined"),
        )
    }
}
