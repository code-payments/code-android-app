package com.flipcash.app.tokens

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.events.TokenInfoEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers every [TokenInfoEntry], asserting the exact event Mixpanel receives. Discovery, Chat and
 * ChatGate are the three entries that split off Wallet in this refactor; the other two keep their
 * source unchanged.
 */
class TokenInfoScreenAnalyticsTest {

    private val mint = Mint.usdc
    private val properties = mapOf("Mint" to PropertyValue.Text(mint.analytics))

    @Test
    fun deeplinkTracksOpenedFromDeeplink() = assertTracksSource(
        entry = TokenInfoEntry.Deeplink,
        expectedName = "Token Info: Opened From Deeplink",
    )

    @Test
    fun walletTracksOpenedFromWallet() = assertTracksSource(
        entry = TokenInfoEntry.Wallet,
        expectedName = "Token Info: Opened From Wallet",
    )

    @Test
    fun discoveryTracksOpenedFromDiscovery() = assertTracksSource(
        entry = TokenInfoEntry.Discovery,
        expectedName = "Token Info: Opened From Discovery",
    )

    @Test
    fun chatTracksOpenedFromChat() = assertTracksSource(
        entry = TokenInfoEntry.Chat,
        expectedName = "Token Info: Opened From Chat",
    )

    @Test
    fun chatGateTracksOpenedFromChatGate() = assertTracksSource(
        entry = TokenInfoEntry.ChatGate,
        expectedName = "Token Info: Opened From Chat Gate",
    )

    private fun assertTracksSource(entry: TokenInfoEntry, expectedName: String) {
        val analytics = RecordingAnalytics()

        analytics.track(TokenInfoEvents.opened(entry.toTokenInfoSource(), mint.analytics))

        assertEquals(
            listOf(AnalyticsEvent(expectedName, properties)),
            analytics.events,
        )
    }
}
