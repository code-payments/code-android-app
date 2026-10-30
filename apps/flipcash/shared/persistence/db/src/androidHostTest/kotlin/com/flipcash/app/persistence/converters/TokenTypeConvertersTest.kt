package com.flipcash.app.persistence.converters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenTypeConvertersTest {

    private val converter = TokenTypeConverters()

    // region SocialLinks

    @Test
    fun `fromSocialLinks and toSocialLinks roundtrip single website`() {
        val original = listOf(SocialLinkSerialized.Website(url = "https://example.com"))
        val serialized = converter.toSocialLinks(original)
        val deserialized = converter.fromSocialLinks(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromSocialLinks and toSocialLinks roundtrip mixed links`() {
        val original = listOf(
            SocialLinkSerialized.Website(url = "https://example.com"),
            SocialLinkSerialized.X(username = "handle"),
            SocialLinkSerialized.Telegram(username = "tguser"),
            SocialLinkSerialized.Discord(inviteCode = "abc123"),
        )
        val serialized = converter.toSocialLinks(original)
        val deserialized = converter.fromSocialLinks(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromSocialLinks returns null for null input`() {
        assertNull(converter.fromSocialLinks(null))
    }

    @Test
    fun `toSocialLinks returns null for null input`() {
        assertNull(converter.toSocialLinks(null))
    }

    @Test
    fun `fromSocialLinks and toSocialLinks roundtrip empty list`() {
        val original = emptyList<SocialLinkSerialized>()
        val serialized = converter.toSocialLinks(original)
        val deserialized = converter.fromSocialLinks(serialized)
        assertEquals(original, deserialized)
    }

    // endregion

    // region BillCustomizations

    @Test
    fun `fromBillCustomizations and toBillCustomizations roundtrip solid background no texture`() {
        val original = BillCustomizationsSerialized(
            background = BillBackgroundSerialized.Solid(colorHex = "#FF0000"),
            texture = null,
            icon = null,
        )
        val serialized = converter.toBillCustomizations(original)
        val deserialized = converter.fromBillCustomizations(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromBillCustomizations and toBillCustomizations roundtrip gradient with texture`() {
        val original = BillCustomizationsSerialized(
            background = BillBackgroundSerialized.Gradient(colors = listOf("#FF0000", "#00FF00", "#0000FF")),
            texture = BillTextureSerialized(index = 2, blendMode = "overlay", strength = 0.75f),
            icon = "aWNvbg==",
        )
        val serialized = converter.toBillCustomizations(original)
        val deserialized = converter.fromBillCustomizations(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromBillCustomizations returns null for null input`() {
        assertNull(converter.fromBillCustomizations(null))
    }

    @Test
    fun `toBillCustomizations returns null for null input`() {
        assertNull(converter.toBillCustomizations(null))
    }

    // endregion

    // region HolderMetrics

    @Test
    fun `fromHolderMetrics and toHolderMetrics roundtrip with deltas`() {
        val original = HolderMetricsSerialized(
            currentHolders = 1500L,
            deltas = listOf(
                HolderDeltaSerialized(range = "DAY", delta = 10L),
                HolderDeltaSerialized(range = "WEEK", delta = -25L),
            ),
        )
        val serialized = converter.toHolderMetrics(original)
        val deserialized = converter.fromHolderMetrics(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromHolderMetrics and toHolderMetrics roundtrip empty deltas`() {
        val original = HolderMetricsSerialized(
            currentHolders = 0L,
            deltas = emptyList(),
        )
        val serialized = converter.toHolderMetrics(original)
        val deserialized = converter.fromHolderMetrics(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromHolderMetrics returns null for null input`() {
        assertNull(converter.fromHolderMetrics(null))
    }

    @Test
    fun `toHolderMetrics returns null for null input`() {
        assertNull(converter.toHolderMetrics(null))
    }

    // endregion
}
