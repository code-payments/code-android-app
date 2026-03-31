package com.getcode.opencode.model.financial

import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.tests.generateRandomPublicKeyForTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MintMetadataTest {

    // Note: Tests for MintMetadata.usdf and TokenWithBalance are skipped because
    // MintMetadata.usdf triggers Ed25519 JNI loading which is unavailable in JVM unit tests.

    // region SocialLink

    @Test
    fun `Website social link stores url directly`() {
        val link = SocialLink.Website(url = "https://example.com")
        assertEquals("https://example.com", link.uri)
    }

    @Test
    fun `X social link builds correct url from username`() {
        val link = SocialLink.X(username = "testuser")
        assertEquals("https://x.com/testuser", link.uri)
    }

    @Test
    fun `Telegram social link builds correct url from username`() {
        val link = SocialLink.Telegram(username = "testgroup")
        assertEquals("https://t.me/testgroup", link.uri)
    }

    @Test
    fun `Discord social link builds correct url from invite code`() {
        val link = SocialLink.Discord(inviteCode = "abc123")
        assertEquals("https://discord.gg/abc123", link.uri)
    }

    // endregion

    // region HolderMetrics

    @Test
    fun `HolderMetrics None has zero holders`() {
        assertEquals(0, HolderMetrics.None.currentHolders)
        assertTrue(HolderMetrics.None.holderDeltas.isEmpty())
    }

    @Test
    fun `deltaForWindow returns delta for matching window`() {
        val metrics = HolderMetrics(
            currentHolders = 100,
            holderDeltas = listOf(
                HolderMetrics.HolderDelta(WindowedRange.LastDay, 5),
                HolderMetrics.HolderDelta(WindowedRange.LastWeek, 20),
                HolderMetrics.HolderDelta(WindowedRange.LastMonth, 50),
            )
        )

        assertEquals(5, metrics.deltaForWindow(WindowedRange.LastDay))
        assertEquals(20, metrics.deltaForWindow(WindowedRange.LastWeek))
        assertEquals(50, metrics.deltaForWindow(WindowedRange.LastMonth))
    }

    @Test
    fun `deltaForWindow returns 0 for missing window`() {
        val metrics = HolderMetrics(
            currentHolders = 100,
            holderDeltas = listOf(
                HolderMetrics.HolderDelta(WindowedRange.LastDay, 5),
            )
        )

        assertEquals(0, metrics.deltaForWindow(WindowedRange.LastYear))
        assertEquals(0, metrics.deltaForWindow(WindowedRange.AllTime))
    }

    @Test
    fun `deltaForWindow on empty deltas returns 0`() {
        val metrics = HolderMetrics(currentHolders = 50, holderDeltas = emptyList())
        assertEquals(0, metrics.deltaForWindow(WindowedRange.LastDay))
    }

    // endregion

    // region LaunchpadMetadata

    @Test
    fun `LaunchpadMetadata stores all fields`() {
        val key = generateRandomPublicKeyForTest()

        val metadata = LaunchpadMetadata(
            currencyConfig = key,
            liquidityPool = key,
            seed = key,
            authority = key,
            mintVault = key,
            coreMintVault = key,
            currentCirculatingSupplyQuarks = 1_000_000_000L,
            sellFeeBps = 100,
            price = Fiat(fiat = 0.001),
            marketCap = Fiat(fiat = 1000.0),
        )

        assertEquals(key, metadata.currencyConfig)
        assertEquals(1_000_000_000L, metadata.currentCirculatingSupplyQuarks)
        assertEquals(100, metadata.sellFeeBps)
        assertEquals(0.001, metadata.price.decimalValue, 0.0001)
        assertEquals(1000.0, metadata.marketCap.decimalValue, 0.01)
    }

    @Test
    fun `two LaunchpadMetadata with same values are equal`() {
        val key = generateRandomPublicKeyForTest()
        val make = {
            LaunchpadMetadata(
                currencyConfig = key,
                liquidityPool = key,
                seed = key,
                authority = key,
                mintVault = key,
                coreMintVault = key,
                currentCirculatingSupplyQuarks = 500L,
                sellFeeBps = 100,
                price = Fiat(fiat = 1.0),
                marketCap = Fiat(fiat = 500.0),
            )
        }

        assertEquals(make(), make())
    }

    // endregion
}
