package com.flipcash.analytics

import com.flipcash.analytics.events.ChatEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatEventsTest {

    @Test
    fun sentMessageCarriesTheChatType() {
        assertEquals(
            AnalyticsEvent("Sent Message", mapOf("Chat Type" to PropertyValue.Text("Group"))),
            ChatEvents.sentMessage(ChatType.GROUP, null),
        )
    }

    @Test
    fun sentMessageCarriesTheError() {
        assertEquals(
            AnalyticsEvent(
                "Sent Message",
                mapOf("Chat Type" to PropertyValue.Text("Contact"), "Error" to PropertyValue.Text("boom")),
            ),
            ChatEvents.sentMessage(ChatType.CONTACT, "boom"),
        )
    }

    @Test
    fun chatTypesKeepTheirWireStrings() {
        assertEquals(
            listOf("Contact", "Tip", "Group", "Unknown"),
            listOf(ChatType.CONTACT, ChatType.TIP, ChatType.GROUP, ChatType.UNKNOWN)
                .map { (ChatEvents.messageReceived(it).properties.getValue("Chat Type") as PropertyValue.Text).value },
        )
    }

    @Test
    fun tipReceivedCarriesTheAmountBlock() {
        assertEquals(
            AnalyticsEvent(
                "Tip Received",
                mapOf(
                    "Chat Type" to PropertyValue.Text("Tip"),
                    "Fiat" to PropertyValue.Number(1.5),
                    "Currency" to PropertyValue.Text("USD"),
                    "USDC" to PropertyValue.Number(1.5),
                    "Quarks" to PropertyValue.Number(25_000_000.0),
                    "Mint" to PropertyValue.Text("mint"),
                ),
            ),
            ChatEvents.tipReceived(
                ChatType.TIP,
                Amount(fiat = 1.5, currency = "USD", usdc = 1.5, quarks = 25_000_000, mint = "mint"),
            ),
        )
    }

    @Test
    fun amountBlockCarriesExchangeRateWhenGiven() {
        val properties = ChatEvents.tipReceived(
            ChatType.TIP,
            Amount(fiat = 2.0, currency = "CAD", usdc = 1.4, quarks = 1, exchangeRate = 1.43, mint = null),
        ).properties
        assertEquals(PropertyValue.Number(1.43), properties["Exchange Rate"])
        assertEquals(null, properties["Mint"])
    }

    @Test
    fun messageReceivedCarriesOnlyTheChatType() {
        assertEquals(
            AnalyticsEvent("Message Received", mapOf("Chat Type" to PropertyValue.Text("Unknown"))),
            ChatEvents.messageReceived(ChatType.UNKNOWN),
        )
    }
    @Test
    fun mutedCarriesEveryDurationByItsWireString() {
        assertEquals(
            listOf("1 Hour", "8 Hours", "1 Week", "Always"),
            MuteDuration.entries.map {
                (ChatEvents.muted(ChatType.GROUP, it, State.SUCCESS, null).properties.getValue("Duration") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun mutedCarriesChatTypeDurationStateAndError() {
        assertEquals(
            AnalyticsEvent(
                "Chat Muted",
                mapOf(
                    "Chat Type" to PropertyValue.Text("Contact"),
                    "Duration" to PropertyValue.Text("8 Hours"),
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("NotFound"),
                ),
            ),
            ChatEvents.muted(ChatType.CONTACT, MuteDuration.EIGHT_HOURS, State.FAILURE, "NotFound"),
        )
    }

    @Test
    fun unmutedCarriesChatTypeAndState() {
        assertEquals(
            AnalyticsEvent(
                "Chat Unmuted",
                mapOf("Chat Type" to PropertyValue.Text("Group"), "State" to PropertyValue.Text("Success")),
            ),
            ChatEvents.unmuted(ChatType.GROUP, State.SUCCESS, null),
        )
    }
}
