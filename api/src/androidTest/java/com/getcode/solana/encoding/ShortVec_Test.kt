package com.getcode.solana.encoding

import com.getcode.solana.ShortVec
import org.junit.Assert
import org.junit.Test

class ShortVec_Test {
    @Test
    fun testEncode() {
        var encoded = ShortVec.encodeLen(0)
        Assert.assertEquals(listOf<Byte>(0), encoded)

        encoded = ShortVec.encodeLen(5)
        Assert.assertEquals(listOf<Byte>(5),  encoded)

        encoded = ShortVec.encodeLen(0x7f)
        Assert.assertEquals(listOf<Byte>(0x7f),  encoded)

        encoded = ShortVec.encodeLen(0x80)
        Assert.assertEquals(listOf(0x80.toByte(), 0x01),  encoded)

        encoded = ShortVec.encodeLen(0xff)
        Assert.assertEquals(listOf(0xff.toByte(), 0x01),  encoded)

        encoded = ShortVec.encodeLen(0x100)
        Assert.assertEquals(listOf(0x80.toByte(), 0x02),  encoded)

        encoded = ShortVec.encodeLen(0x7fff)
        Assert.assertEquals(listOf(0xff.toByte(), 0xff.toByte(), 0x01), encoded)
    }

    @Test
    fun testEncodeComponents() {
        val components = listOf(
                listOf<Byte>(1, 2, 3, 4),
                listOf<Byte>(5, 6, 7, 8),
                listOf<Byte>(9, 8, 7, 6),
                listOf<Byte>(4, 3, 2, 1),
        )

        val data = ShortVec.encodeList(components)

        Assert.assertEquals(17, data.size)
        Assert.assertEquals(4, data[0].toInt())

        val (length, remaining) = ShortVec.decodeLen(data)

        Assert.assertEquals(4, length)
        Assert.assertEquals(components[0], remaining.subList(0, 4))
        Assert.assertEquals(components[1], remaining.subList(4, 8))
        Assert.assertEquals(components[2], remaining.subList(8, 12))
        Assert.assertEquals(components[3], remaining.subList(12, 16))
    }

    @Test
    fun testDecode() {
        var decoded = ShortVec.decodeLen(listOf(0))
        Assert.assertEquals(0, decoded.first)

        decoded = ShortVec.decodeLen(listOf(5))
        Assert.assertEquals(5, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0x7f))
        Assert.assertEquals(0x7f, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0x80.toByte(), 0x01))
        Assert.assertEquals(0x80, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0xff.toByte(), 0x01))
        Assert.assertEquals(0xff, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0x80.toByte(), 0x02))
        Assert.assertEquals(0x100, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0xff.toByte(), 0xff.toByte(), 0x01))
        Assert.assertEquals(0x7fff, decoded.first)

        decoded = ShortVec.decodeLen(listOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01))
        Assert.assertEquals(0x200000, decoded.first)
    }

    @Test
    fun testValidity() {
        for (i in 0 until 255) {
            val input = ShortVec.encodeLen(i)
            val actual = ShortVec.decodeLen(input)
            Assert.assertEquals(actual.first, i)
        }
    }
}