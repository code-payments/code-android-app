package com.flipcash.app.core.feed

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityFeedMessageTest {

    // --- MessageState.from ---

    @Test
    fun fromPending() {
        assertEquals(MessageState.PENDING, MessageState.from("pending"))
    }

    @Test
    fun fromCompleted() {
        assertEquals(MessageState.COMPLETED, MessageState.from("completed"))
    }

    @Test
    fun fromUnknown() {
        assertEquals(MessageState.UNKNOWN, MessageState.from("unknown"))
    }

    @Test
    fun fromUppercase() {
        assertEquals(MessageState.PENDING, MessageState.from("PENDING"))
    }

    @Test
    fun fromMixedCase() {
        assertEquals(MessageState.COMPLETED, MessageState.from("Completed"))
    }

    @Test
    fun fromInvalidFallsBackToUnknown() {
        assertEquals(MessageState.UNKNOWN, MessageState.from("invalid"))
    }

    @Test
    fun fromEmptyFallsBackToUnknown() {
        assertEquals(MessageState.UNKNOWN, MessageState.from(""))
    }

    // --- MessageMetadata.from ---

    @Test
    fun metadataFromNullReturnsNull() {
        assertNull(MessageMetadata.from(null))
    }

    @Test
    fun metadataFromInvalidJsonReturnsUnknown() {
        assertEquals(MessageMetadata.Unknown, MessageMetadata.from("not json"))
    }

    @Test
    fun metadataFromDirectlySentCrypto() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.DirectlySentCrypto"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.DirectlySentCrypto(), result)
    }

    @Test
    fun metadataFromReceivedCrypto() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.ReceivedCrypto"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.ReceivedCrypto(), result)
    }

    @Test
    fun metadataFromWithdrewCrypto() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.WithdrewCrypto"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.WithdrewCrypto(), result)
    }

    @Test
    fun metadataFromDepositedCrypto() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.DepositedCrypto"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.DepositedCrypto, result)
    }

    @Test
    fun metadataFromBoughtToken() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.BoughtToken"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.BoughtToken, result)
    }

    @Test
    fun metadataFromSoldToken() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.SoldToken"}"""
        val result = MessageMetadata.from(json)
        assertEquals(MessageMetadata.SoldToken, result)
    }

    // --- Swap metadata ---

    @Test
    fun swappedCryptoRoundTrips() {
        val original: MessageMetadata = MessageMetadata.SwappedCrypto(
            swap = SwappedCryptoMetadata(
                from = LocalFiat(usdf = Fiat(quarks = 5_000_000L), nativeAmount = Fiat(fiat = 5.0)),
                toMint = PublicKey(ByteArray(32) { it.toByte() }.toList()),
                toAmount = LocalFiat(usdf = Fiat(quarks = 4_900_000L), nativeAmount = Fiat(fiat = 4.9)),
                fee = Fiat(fiat = 0.1),
                swapState = SwapState.SUCCEEDED,
            )
        )
        assertEquals(original, MessageMetadata.from(Json.encodeToString(original)))
    }

    @Test
    fun withdrewCryptoWithSwapMetadataRoundTrips() {
        val original: MessageMetadata = MessageMetadata.WithdrewCrypto(
            swapMetadata = SwappedCryptoMetadata(
                from = LocalFiat(usdf = Fiat(quarks = 5_000_000L), nativeAmount = Fiat(fiat = 5.0)),
                toMint = PublicKey(ByteArray(32) { it.toByte() }.toList()),
                toAmount = null,
                fee = Fiat(fiat = 0.1),
                swapState = SwapState.PENDING,
            )
        )
        assertEquals(original, MessageMetadata.from(Json.encodeToString(original)))
    }

    // Legacy withdrew-crypto JSON (no swapMetadata) still decodes, with a null swap.
    @Test
    fun legacyWithdrewCryptoDecodesWithNullSwap() {
        val json = """{"type":"com.flipcash.app.core.feed.MessageMetadata.WithdrewCrypto"}"""
        assertEquals(MessageMetadata.WithdrewCrypto(swapMetadata = null), MessageMetadata.from(json))
    }
}
