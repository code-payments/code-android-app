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
                AnalyticsEvent("Token Info: Opened From Chat", mint),
                AnalyticsEvent("Token Info: Opened From Chat Gate", mint),
                AnalyticsEvent("Token Info: Opened From Give", mint),
                AnalyticsEvent("Token Info: Opened From Send", mint),
            ),
            listOf(
                TokenInfoSource.DEEPLINK,
                TokenInfoSource.WALLET,
                TokenInfoSource.DISCOVERY,
                TokenInfoSource.CHAT,
                TokenInfoSource.CHAT_GATE,
                TokenInfoSource.GIVE,
                TokenInfoSource.SEND,
            ).map { TokenInfoEvents.opened(it, "mint") },
        )
    }
}
