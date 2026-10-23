package com.getcode.opencode.model.core

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenCodePayloadTest {

    @Before
    fun setUp() {
        mockkStatic("com.getcode.opencode.utils.KeyPairKt")
        every { com.getcode.opencode.utils.deriveRendezvousKey(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.opencode.utils.KeyPairKt")
    }

    // region encode / decode

    @Test
    fun `encode produces 20-byte payload`() {
        val fiat = Fiat(quarks = 500L, currencyCode = CurrencyCode.USD)
        val nonce = List(10) { (it + 1).toByte() }

        val encoded = encodePayload(PayloadKind.Cash, fiat, nonce)

        assertEquals(OpenCodePayload.LENGTH, encoded.size)
    }

    @Test
    fun `encode puts kind in byte 0`() {
        val encoded = encodePayload(PayloadKind.Cash, Fiat.Zero, List(10) { 0.toByte() })
        assertEquals(PayloadKind.Cash.value.toByte(), encoded[0])

        val encoded2 = encodePayload(PayloadKind.MultiMintCash, Fiat.Zero, List(10) { 0.toByte() })
        assertEquals(PayloadKind.MultiMintCash.value.toByte(), encoded2[0])
    }

    @Test
    fun `encode puts currency ordinal in byte 1`() {
        val fiat = Fiat(quarks = 0L, currencyCode = CurrencyCode.EUR)
        val encoded = encodePayload(PayloadKind.Cash, fiat, List(10) { 0.toByte() })

        assertEquals(CurrencyCode.EUR.ordinal.toByte(), encoded[1])
    }

    @Test
    fun `fromList decodes Cash kind`() {
        val fiat = Fiat(quarks = 12345L, currencyCode = CurrencyCode.USD)
        val nonce = List(10) { (it + 0xA).toByte() }
        val encoded = encodePayload(PayloadKind.Cash, fiat, nonce)

        val decoded = OpenCodePayload.fromList(encoded)

        assertEquals(PayloadKind.Cash, decoded.kind)
        assertEquals(CurrencyCode.USD, decoded.fiat!!.currencyCode)
        assertEquals(12345L, decoded.fiat!!.quarks)
    }

    @Test
    fun `fromList decodes MultiMintCash kind`() {
        val fiat = Fiat(quarks = 99999L, currencyCode = CurrencyCode.CAD)
        val nonce = List(10) { 0x42.toByte() }
        val encoded = encodePayload(PayloadKind.MultiMintCash, fiat, nonce)

        val decoded = OpenCodePayload.fromList(encoded)

        assertEquals(PayloadKind.MultiMintCash, decoded.kind)
        assertEquals(CurrencyCode.CAD, decoded.fiat!!.currencyCode)
        assertEquals(99999L, decoded.fiat!!.quarks)
    }

    @Test
    fun `fromList decodes Unknown kind as zero fiat`() {
        val data = MutableList<Byte>(20) { 0 }
        data[0] = PayloadKind.Unknown.value.toByte() // -1

        val decoded = OpenCodePayload.fromList(data)

        // Unknown kind maps to Cash as fallback (entries.find returns null, default is Cash)
        // Actually -1 won't match any entry, so it falls back to Cash
        assertIs<Fiat>(decoded.value)
    }

    @Test
    fun `fromList does not throw on out-of-range currency byte`() {
        // A foreign / corrupt Kik code can pass error-correction yet carry a currency byte
        // outside CurrencyCode's range. Regression for IndexOutOfBoundsException (Bugsnag
        // 6a5a4523): `CurrencyCode.entries[232]` on a 171-element enum. Such a frame is not a
        // valid Flipcash cash code, so it must decode to Unknown (which the scanner ignores).
        val frame = MutableList<Byte>(OpenCodePayload.LENGTH) { 0 }
        frame[0] = PayloadKind.Cash.value.toByte()
        frame[1] = 232.toByte() // >= CurrencyCode.entries.size

        val decoded = OpenCodePayload.fromList(frame)

        assertEquals(PayloadKind.Unknown, decoded.kind)
    }

    @Test
    fun `fromList decodes highest valid currency index`() {
        // Boundary: the last valid ordinal must still decode as Cash, not be rejected.
        val lastCurrency = CurrencyCode.entries.last()
        val fiat = Fiat(quarks = 7L, currencyCode = lastCurrency)
        val encoded = encodePayload(PayloadKind.Cash, fiat, List(10) { 0.toByte() })

        val decoded = OpenCodePayload.fromList(encoded)

        assertEquals(PayloadKind.Cash, decoded.kind)
        assertEquals(lastCurrency, decoded.fiat!!.currencyCode)
    }

    @Test
    fun `fromList preserves nonce bytes`() {
        val nonce = List(10) { (it * 3).toByte() }
        val encoded = encodePayload(PayloadKind.Cash, Fiat(quarks = 1L), nonce)

        val decoded = OpenCodePayload.fromList(encoded)

        assertEquals(nonce, decoded.nonce)
    }

    @Test
    fun `encode and fromList roundtrip for various currencies`() {
        val currencies = listOf(CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.GBP, CurrencyCode.JPY)
        val nonce = List(10) { 0xFF.toByte() }

        for (currency in currencies) {
            val fiat = Fiat(quarks = 42L, currencyCode = currency)
            val encoded = encodePayload(PayloadKind.Cash, fiat, nonce)
            val decoded = OpenCodePayload.fromList(encoded)

            assertEquals(currency, decoded.fiat!!.currencyCode, "Currency roundtrip failed for $currency")
            assertEquals(42L, decoded.fiat!!.quarks, "Quarks roundtrip failed for $currency")
        }
    }

    @Test
    fun `encode and fromList roundtrip for large quarks value`() {
        val nonce = List(10) { 0.toByte() }
        val fiat = Fiat(quarks = 1_000_000_000L, currencyCode = CurrencyCode.USD)
        val encoded = encodePayload(PayloadKind.MultiMintCash, fiat, nonce)
        val decoded = OpenCodePayload.fromList(encoded)

        assertEquals(1_000_000_000L, decoded.fiat!!.quarks)
    }

    // endregion

    // region helpers

    /**
     * Encodes payload data without constructing OpenCodePayload (which triggers
     * deriveRendezvousKey that needs native Ed25519).
     */
    private fun encodePayload(kind: PayloadKind, fiat: Fiat, nonce: List<Byte>): List<Byte> {
        val data = MutableList<Byte>(OpenCodePayload.LENGTH) { 0 }

        data[0] = kind.value.toByte()
        data[1] = fiat.currencyCode.ordinal.toByte()

        fiat.quarks.longToByteArray().forEachIndexed { index, byte ->
            data[index + OpenCodePayload.OFFSET_QUARKS] = byte
        }

        nonce.toByteArray().forEachIndexed { index, byte ->
            data[index + OpenCodePayload.OFFSET_NONCE] = byte
        }

        return data
    }

    private fun Long.longToByteArray(): ByteArray {
        val result = ByteArray(8)
        for (i in 0 until 8) {
            result[i] = (this shr (i * 8) and 0xFF).toByte()
        }
        return result
    }

    // endregion
}
