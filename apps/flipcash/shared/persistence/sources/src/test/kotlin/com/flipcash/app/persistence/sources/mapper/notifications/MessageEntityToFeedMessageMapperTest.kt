package com.flipcash.app.persistence.sources.mapper.notifications

import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.persistence.entities.MessageEntity
import com.getcode.opencode.model.financial.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class MessageEntityToFeedMessageMapperTest {

    private val mapper = MessageEntityToFeedMessageMapper()

    // Hardcoded mint addresses to avoid Mint companion object (uses Base58 + android deps)
    private val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    private val USDF_MINT = "5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ"

    private fun entity(
        amountUsdc: Long? = 100_000L,
        amountNative: Long? = 200_000L,
        nativeCurrency: String? = "cad",
        rate: Double? = 1.35,
        mintBase58: String? = USDC_MINT,
        state: String = "COMPLETED",
        metadata: String? = null,
        timestamp: Long = 1_700_000_000_000L,
    ) = MessageEntity(
        idBase58 = "11111111111111111111111111111111",
        text = "test message",
        amountUsdc = amountUsdc,
        amountNative = amountNative,
        nativeCurrency = nativeCurrency,
        rate = rate,
        state = state,
        timestamp = timestamp,
        metadata = metadata,
        mintBase58 = mintBase58,
    )

    // --- Amount resolution ---

    @Test
    fun amountIsNonNullWhenAllFieldsPresent() {
        val result = mapper.map(entity())
        assertNotNull(result.amount)
        assertEquals(CurrencyCode.USD, result.amount!!.underlyingTokenAmount.currencyCode)
        assertEquals(CurrencyCode.CAD, result.amount!!.nativeAmount.currencyCode)
    }

    @Test
    fun amountIsNullWhenAmountUsdcMissing() {
        val result = mapper.map(entity(amountUsdc = null))
        assertNull(result.amount)
    }

    @Test
    fun amountIsNullWhenAmountNativeMissing() {
        val result = mapper.map(entity(amountNative = null))
        assertNull(result.amount)
    }

    @Test
    fun amountIsNullWhenCurrencyMissing() {
        val result = mapper.map(entity(nativeCurrency = null))
        assertNull(result.amount)
    }

    @Test
    fun amountIsNullWhenRateMissing() {
        val result = mapper.map(entity(rate = null))
        assertNull(result.amount)
    }

    @Test
    fun amountIsNullWhenMintMissing() {
        val result = mapper.map(entity(mintBase58 = null))
        assertNull(result.amount)
    }

    @Test
    fun amountIsNullWhenInvalidCurrency() {
        val result = mapper.map(entity(nativeCurrency = "zzz_invalid"))
        assertNull(result.amount)
    }

    // --- USDF mint path ---

    @Test
    fun usdfMintUsesUsdfLocalFiatConstructor() {
        val result = mapper.map(entity(mintBase58 = USDF_MINT))
        assertNotNull(result.amount)
        assertEquals(CurrencyCode.USD, result.amount!!.underlyingTokenAmount.currencyCode)
    }

    // --- Timestamp mapping ---

    @Test
    fun timestampMappedCorrectly() {
        val ts = 1_700_000_000_000L
        val result = mapper.map(entity(timestamp = ts))
        assertEquals(Instant.fromEpochMilliseconds(ts), result.timestamp)
    }

    // --- State mapping ---

    @Test
    fun stateMapsCompleted() {
        val result = mapper.map(entity(state = "COMPLETED"))
        assertEquals(MessageState.COMPLETED, result.state)
    }

    @Test
    fun stateMapsUnknownForInvalidValue() {
        val result = mapper.map(entity(state = "not_a_real_state"))
        assertEquals(MessageState.UNKNOWN, result.state)
    }

    // --- Metadata mapping ---

    @Test
    fun metadataNullWhenEntityMetadataNull() {
        val result = mapper.map(entity(metadata = null))
        assertNull(result.metadata)
    }

    // --- Text passthrough ---

    @Test
    fun textPassedThrough() {
        val result = mapper.map(entity())
        assertEquals("test message", result.text)
    }
}
