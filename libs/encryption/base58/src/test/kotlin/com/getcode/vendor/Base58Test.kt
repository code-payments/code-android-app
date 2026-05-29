package com.getcode.vendor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base58Test {

    @Test
    fun encodeEmpty() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test
    fun encodeDecodeRoundtrip() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base58.encode(data)
        val decoded = Base58.decode(encoded)
        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun decodeEmpty() {
        assertEquals(0, Base58.decode("").size)
    }

    @Test
    fun encodeLeadingZeros() {
        // Leading zero bytes should produce leading '1' characters
        val data = byteArrayOf(0, 0, 1)
        val encoded = Base58.encode(data)
        assertTrue(encoded.startsWith("11"))
    }

    @Test
    fun decodeLeadingOnes() {
        // Leading '1' chars decode to leading zero bytes
        val decoded = Base58.decode("11BXF")
        assertEquals(0, decoded[0])
        assertEquals(0, decoded[1])
    }

    @Test
    fun knownVector() {
        // "Hello World" in Base58
        val data = "Hello World".toByteArray(Charsets.UTF_8)
        val encoded = Base58.encode(data)
        val decoded = Base58.decode(encoded)
        assertEquals("Hello World", String(decoded, Charsets.UTF_8))
    }

    @Test
    fun decodeInvalidCharacterThrows() {
        assertFailsWith<Base58.AddressFormatException.InvalidCharacter> {
            Base58.decode("0OIl") // '0', 'O', 'I', 'l' are not in Base58 alphabet
        }
    }

    @Test
    fun encodeCheckedRoundtrip() {
        val payload = byteArrayOf(10, 20, 30)
        val encoded = Base58.encodeChecked(0, payload)
        val decoded = Base58.decodeChecked(encoded)
        // First byte is version
        assertEquals(0, decoded[0])
        assertTrue(payload.contentEquals(decoded.copyOfRange(1, decoded.size)))
    }

    @Test
    fun decodeCheckedInvalidChecksumThrows() {
        val encoded = Base58.encodeChecked(0, byteArrayOf(1, 2, 3))
        // Tamper with a character
        val tampered = encoded.dropLast(1) + if (encoded.last() == '2') "3" else "2"
        assertFailsWith<Base58.AddressFormatException> {
            Base58.decodeChecked(tampered)
        }
    }

    @Test
    fun decodeCheckedTooShortThrows() {
        assertFailsWith<Base58.AddressFormatException.InvalidDataLength> {
            Base58.decodeChecked("1") // decodes to too few bytes
        }
    }

    @Test
    fun decodeToBigInteger() {
        val data = byteArrayOf(1, 0) // 256 in big-endian
        val encoded = Base58.encode(data)
        val bigInt = Base58.decodeToBigInteger(encoded)
        assertEquals(256.toBigInteger(), bigInt)
    }

    @Test
    fun allZeroBytes() {
        val data = ByteArray(5)
        val encoded = Base58.encode(data)
        assertEquals("11111", encoded)
        val decoded = Base58.decode(encoded)
        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun singleByte() {
        for (b in 0..255) {
            val data = byteArrayOf(b.toByte())
            val encoded = Base58.encode(data)
            val decoded = Base58.decode(encoded)
            assertTrue(data.contentEquals(decoded), "Roundtrip failed for byte $b")
        }
    }
}
