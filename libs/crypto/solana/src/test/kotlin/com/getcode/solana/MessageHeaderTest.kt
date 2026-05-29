package com.getcode.solana

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageHeaderTest {

    @Test
    fun encodeProducesThreeBytes() {
        val header = MessageHeader(
            requiredSignatures = 2,
            readOnlySigners = 1,
            readOnly = 3,
        )
        val encoded = header.encode()
        assertEquals(3, encoded.size)
    }

    @Test
    fun encodeContainsCorrectValues() {
        val header = MessageHeader(
            requiredSignatures = 2,
            readOnlySigners = 1,
            readOnly = 3,
        )
        val encoded = header.encode()
        assertEquals(2.toByte(), encoded[0])
        assertEquals(1.toByte(), encoded[1])
        assertEquals(3.toByte(), encoded[2])
    }

    @Test
    fun encodeFromListRoundtrip() {
        val header = MessageHeader(
            requiredSignatures = 5,
            readOnlySigners = 2,
            readOnly = 4,
        )
        val encoded = header.encode()
        val decoded = MessageHeader.fromList(encoded.toList())

        assertEquals(header.requiredSignatures, decoded.requiredSignatures)
        assertEquals(header.readOnlySigners, decoded.readOnlySigners)
        assertEquals(header.readOnly, decoded.readOnly)
    }

    @Test
    fun fromListDecodesCorrectly() {
        val bytes = listOf<Byte>(10, 3, 7)
        val header = MessageHeader.fromList(bytes)

        assertEquals(10, header.requiredSignatures)
        assertEquals(3, header.readOnlySigners)
        assertEquals(7, header.readOnly)
    }

    @Test
    fun equality() {
        val header1 = MessageHeader(
            requiredSignatures = 1,
            readOnlySigners = 2,
            readOnly = 3,
        )
        val header2 = MessageHeader(
            requiredSignatures = 1,
            readOnlySigners = 2,
            readOnly = 3,
        )
        assertEquals(header1, header2)
    }

    @Test
    fun headerLengthConstant() {
        assertEquals(3, MessageHeader.length)
    }

    @Test
    fun encodeZeroValues() {
        val header = MessageHeader(
            requiredSignatures = 0,
            readOnlySigners = 0,
            readOnly = 0,
        )
        val encoded = header.encode()
        assertTrue(encoded.all { it == 0.toByte() })
    }
}
