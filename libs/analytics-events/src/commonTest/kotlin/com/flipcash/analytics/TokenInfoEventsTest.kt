package com.flipcash.analytics

import com.flipcash.analytics.events.TokenInfoEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenInfoEventsTest {

    private val mint = mapOf("Mint" to PropertyValue.Text("mint"))

    @Test
    fun eachSourceIsItsOwnEvent() {
        assertEquals(
            listOf(
                AnalyticsEvent("Token Info: Opened From Deeplink", mint),
                AnalyticsEvent("Token Info: Opened From Wallet", mint),
                AnalyticsEvent("Token Info: Opened From Discovery", mint),
                AnalyticsEvent("Token Info: Opened From Search", mint),
                AnalyticsEvent("Token Info: Opened From Chat", mint),
                AnalyticsEvent("Token Info: Opened From Chat Gate", mint),
                AnalyticsEvent("Token Info: Opened From Give", mint),
                AnalyticsEvent("Token Info: Opened From Send", mint),
            ),
            listOf(
                TokenInfoSource.DEEPLINK,
                TokenInfoSource.WALLET,
                TokenInfoSource.DISCOVERY,
                TokenInfoSource.SEARCH,
                TokenInfoSource.CHAT,
                TokenInfoSource.CHAT_GATE,
                TokenInfoSource.GIVE,
                TokenInfoSource.SEND,
            ).map { TokenInfoEvents.opened(it, "mint") },
        )
    }

    @Test
    fun tileTappedCarriesEveryTileByItsWireString() {
        assertEquals(
            listOf("Send", "Buy", "Buy In", "Transactions", "More"),
            CurrencyInfoTile.entries.map {
                val event = TokenInfoEvents.tileTapped(it, "mint")
                assertEquals(
                    AnalyticsEvent("Token Info: Tile Tapped", mapOf("Tile" to event.properties.getValue("Tile")) + mint),
                    event,
                )
                (event.properties.getValue("Tile") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun moreOptionPickedCarriesEveryOptionByItsWireString() {
        assertEquals(
            listOf("Convert", "Withdraw", "Deposit"),
            CurrencyInfoMoreOption.entries.map {
                val event = TokenInfoEvents.moreOptionPicked(it, "mint")
                assertEquals(
                    AnalyticsEvent("Token Info: More Option Picked", mapOf("Option" to event.properties.getValue("Option")) + mint),
                    event,
                )
                (event.properties.getValue("Option") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun communityChatTappedCarriesTheMint() {
        assertEquals(AnalyticsEvent("Token Info: Community Chat Tapped", mint), TokenInfoEvents.communityChatTapped("mint"))
    }
}
