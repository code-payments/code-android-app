package com.flipcash.app.tokens

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PropertyValue
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals

/** The wallet card-expand overlay sends the same open events iOS does for its currency-info card. */
class CurrencyInfoExpansionAnalyticsTest {

    private val mint = Mint.usdc
    private val properties = mapOf("Mint" to PropertyValue.Text(mint.analytics))

    @Test
    fun deckCardTapTracksOpenedFromWallet() = assertTracks(
        fromDeckCard = true,
        expectedName = "Token Info: Opened From Wallet",
    )

    @Test
    fun sourcelessOpenTracksOpenedFromDeeplink() = assertTracks(
        fromDeckCard = false,
        expectedName = "Token Info: Opened From Deeplink",
    )

    private fun assertTracks(fromDeckCard: Boolean, expectedName: String) {
        val analytics = RecordingAnalytics()

        analytics.track(cardExpansionOpened(mint, fromDeckCard))

        assertEquals(listOf(AnalyticsEvent(expectedName, properties)), analytics.events)
    }
}
