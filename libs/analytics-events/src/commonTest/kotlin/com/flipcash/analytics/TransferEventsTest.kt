package com.flipcash.analytics

import com.flipcash.analytics.events.TransferEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferEventsTest {

    private val amount = Amount(fiat = 5.0, currency = "USD", usdc = 5.0, quarks = 500, exchangeRate = 1.0, mint = "mint")
    private val amountProperties = mapOf(
        "Fiat" to PropertyValue.Number(5.0),
        "Currency" to PropertyValue.Text("USD"),
        "USDC" to PropertyValue.Number(5.0),
        "Quarks" to PropertyValue.Number(500.0),
        "Exchange Rate" to PropertyValue.Number(1.0),
        "Mint" to PropertyValue.Text("mint"),
    )

    @Test
    fun startsCarryNoProperties() {
        assertEquals(AnalyticsEvent("Grab Bill Start", emptyMap()), TransferEvents.grabBillStart())
        assertEquals(AnalyticsEvent("Give Bill Start", emptyMap()), TransferEvents.giveBillStart())
    }

    @Test
    fun grabBillCarriesGrabTimeAsANumber() {
        assertEquals(
            AnalyticsEvent(
                "Grab Bill",
                mapOf("Grab Time" to PropertyValue.Number(1200.0), "State" to PropertyValue.Text("Success")) + amountProperties,
            ),
            TransferEvents.grabBill(State.SUCCESS, amount, 1200, null),
        )
    }

    @Test
    fun grabBillFailureOmitsWhatItDoesNotHave() {
        assertEquals(
            AnalyticsEvent(
                "Grab Bill",
                mapOf("State" to PropertyValue.Text("Failure"), "Error" to PropertyValue.Text("timed out")),
            ),
            TransferEvents.grabBill(State.FAILURE, null, null, "timed out"),
        )
    }

    @Test
    fun outcomeEventsCarryStateAmountAndError() {
        val expected = mapOf("State" to PropertyValue.Text("Failure")) + amountProperties +
            ("Error" to PropertyValue.Text("e"))
        assertEquals(AnalyticsEvent("Give Bill", expected), TransferEvents.giveBill(State.FAILURE, amount, "e"))
        assertEquals(AnalyticsEvent("Withdrawal", expected), TransferEvents.withdrawal(State.FAILURE, amount, "e"))
        assertEquals(AnalyticsEvent("Receive Cash Link", expected), TransferEvents.receiveCashLink(State.FAILURE, amount, "e"))
        assertEquals(AnalyticsEvent("Sent Cash", expected), TransferEvents.sentCash(State.FAILURE, amount, "e"))
        assertEquals(AnalyticsEvent("Sent Tip", expected), TransferEvents.sentTip(State.FAILURE, amount, "e"))
    }

    @Test
    fun sendCashLinkToClipboard() {
        assertEquals(
            AnalyticsEvent(
                "Send Cash Link",
                mapOf(
                    "Cash Link Choice" to PropertyValue.Text("Copied to clipboard"),
                    "State" to PropertyValue.Text("Success"),
                ) + amountProperties,
            ),
            TransferEvents.sendCashLink(State.SUCCESS, amount, CashLinkChoice.COPIED, null, null),
        )
    }

    @Test
    fun sendCashLinkToAnApp() {
        assertEquals(
            AnalyticsEvent(
                "Send Cash Link",
                mapOf(
                    "Cash Link Choice" to PropertyValue.Text("Shared to app"),
                    "App" to PropertyValue.Text("com.whatsapp"),
                    "State" to PropertyValue.Text("Success"),
                ),
            ),
            TransferEvents.sendCashLink(State.SUCCESS, null, CashLinkChoice.SHARED, "com.whatsapp", null),
        )
    }

    @Test
    fun sendCashLinkToAGroupChatThatFailed() {
        assertEquals(
            AnalyticsEvent(
                "Send Cash Link",
                mapOf(
                    "Cash Link Choice" to PropertyValue.Text("Posted to group chat"),
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("offline"),
                ),
            ),
            TransferEvents.sendCashLink(State.FAILURE, null, CashLinkChoice.GROUP_CHAT, null, "offline"),
        )
    }
}
