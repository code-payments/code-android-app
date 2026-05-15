package com.getcode.solana

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgoraMemoTest {

    // Note: encode/decode roundtrip tests require Android Base64 and cannot run as JVM unit tests.
    // Testing construction, validation, and field access instead.

    @Test
    fun constructionWithDefaults() {
        val memo = AgoraMemo(transferType = TransferType.none, appIndex = 0)
        assertEquals(MagicByte.default, memo.magicByte)
        assertEquals(1.toByte(), memo.version)
        assertEquals(TransferType.none, memo.transferType)
        assertEquals(0, memo.appIndex)
    }

    @Test
    fun constructionWithAllTransferTypes() {
        for (type in listOf(TransferType.none, TransferType.earn, TransferType.spend, TransferType.p2p)) {
            val memo = AgoraMemo(transferType = type, appIndex = 10)
            assertEquals(type, memo.transferType)
        }
    }

    @Test
    fun foreignKeyPaddedToByteLength() {
        val shortKey = listOf<Byte>(1, 2, 3)
        val memo = AgoraMemo(transferType = TransferType.earn, appIndex = 0, bytes = shortKey)
        assertEquals(AgoraMemo.byteLength, memo.bytes.size)
        assertEquals(1.toByte(), memo.bytes[0])
        assertEquals(2.toByte(), memo.bytes[1])
        assertEquals(3.toByte(), memo.bytes[2])
        // Remaining bytes should be zero-padded
        for (i in 3 until AgoraMemo.byteLength) {
            assertEquals(0.toByte(), memo.bytes[i])
        }
    }

    @Test
    fun foreignKeyFullLength() {
        val fullKey = List(AgoraMemo.byteLength) { (it + 1).toByte() }
        val memo = AgoraMemo(transferType = TransferType.p2p, appIndex = 42, bytes = fullKey)
        assertEquals(fullKey, memo.bytes)
    }

    @Test
    fun magicByteValidValues() {
        MagicByte(1)
        MagicByte(2)
        MagicByte(3)
    }

    @Test
    fun magicByteInvalidZeroThrows() {
        assertFailsWith<IllegalArgumentException> {
            MagicByte(0)
        }
    }

    @Test
    fun magicByteInvalidFourThrows() {
        assertFailsWith<IllegalArgumentException> {
            MagicByte(4)
        }
    }

    @Test
    fun magicByteEquality() {
        assertEquals(MagicByte(1), MagicByte(1))
    }

    @Test
    fun magicByteHashCode() {
        assertEquals(MagicByte(1).hashCode(), MagicByte(1).hashCode())
    }

    @Test
    fun transferTypeGetByValueNone() {
        assertEquals(TransferType.none, TransferType.getByValue(0))
    }

    @Test
    fun transferTypeGetByValueEarn() {
        assertEquals(TransferType.earn, TransferType.getByValue(1))
    }

    @Test
    fun transferTypeGetByValueSpend() {
        assertEquals(TransferType.spend, TransferType.getByValue(2))
    }

    @Test
    fun transferTypeGetByValueP2p() {
        assertEquals(TransferType.p2p, TransferType.getByValue(3))
    }

    @Test
    fun transferTypeGetByValueUnknown() {
        assertEquals(TransferType.unknown, TransferType.getByValue(99))
    }

    @Test
    fun companionConstants() {
        assertEquals(2, AgoraMemo.magicByteBitLength)
        assertEquals(3, AgoraMemo.versionBitLength)
        assertEquals(5, AgoraMemo.transferTypeBitLength)
        assertEquals(16, AgoraMemo.appIndexBitLength)
        assertEquals(230, AgoraMemo.foreignKeyBitLength)
        assertEquals(28, AgoraMemo.byteLength) // 230 / 8
        assertEquals(32, AgoraMemo.totalByteCount)
    }
}
