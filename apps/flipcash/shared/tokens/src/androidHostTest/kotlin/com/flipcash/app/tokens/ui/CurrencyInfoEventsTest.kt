package com.flipcash.app.tokens.ui

import com.flipcash.analytics.CurrencyInfoTile
import com.flipcash.analytics.events.TokenInfoEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every tile maps to its own value, and each event carries the mint it was tapped on. */
class CurrencyInfoEventsTest {

    private val mint = Mint.usdc

    @Test
    fun `each tile sends Tile Tapped with its own value and the mint`() {
        val analytics = RecordingAnalytics()

        CurrencyActionTile.entries.forEach { analytics.trackTileTapped(it, mint) }

        assertEquals(
            listOf(
                CurrencyInfoTile.SEND,
                CurrencyInfoTile.BUY,
                CurrencyInfoTile.BUY_IN,
                CurrencyInfoTile.TRANSACTIONS,
                CurrencyInfoTile.MORE,
            ).map { TokenInfoEvents.tileTapped(it, mint.analytics) },
            analytics.events,
        )
    }

    @Test
    fun `a community chat tap sends the currency's mint`() {
        val analytics = RecordingAnalytics()

        analytics.trackCommunityChatTapped(mint)

        assertEquals(listOf(TokenInfoEvents.communityChatTapped(mint.analytics)), analytics.events)
    }
}
