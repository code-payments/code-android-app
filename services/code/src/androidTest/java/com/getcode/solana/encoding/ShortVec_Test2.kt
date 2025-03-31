package com.getcode.solana.encoding

import com.getcode.solana.ShortVec
import com.getcode.utils.toByteList
import junit.framework.Assert.assertEquals
import org.junit.Test

class ShortVecTest {
    @Test
    fun testEncode() {
        var encoded = ShortVec.encodeLen(0)
        assertEquals(listOf(0).toByteList(), encoded)

        encoded = ShortVec.encodeLen(5)
        assertEquals(listOf(5).toByteList(), encoded)

        encoded = ShortVec.encodeLen(0x7f)
        assertEquals(listOf(0x7f).toByteList(), encoded)

        encoded = ShortVec.encodeLen(0x80)
        assertEquals(listOf(0x80, 0x01).toByteList(), encoded)

        encoded = ShortVec.encodeLen(0xff)
        assertEquals(listOf(0xff, 0x01).toByteList(), encoded)

        encoded = ShortVec.encodeLen(0x100)
        assertEquals(listOf(0x80, 0x02).toByteList(), encoded)

        encoded = ShortVec.encodeLen(0x7fff)
        assertEquals(listOf(0xff, 0xff, 0x01).toByteList(), encoded)
    }

    @Test
    fun testEncodeComponents() {
        val components = listOf(
            listOf(1, 2, 3, 4).toByteList(),
            listOf(5, 6, 7, 8).toByteList(),
            listOf(9, 8, 7, 6).toByteList(),
            listOf(4, 3, 2, 1).toByteList(),
        )

        val data = ShortVec.encodeList(components)

        assertEquals(17, data.size)
        assertEquals(4, data[0])

        val (length, remaining) = ShortVec.decodeLen(data)

        assertEquals(length, 4)
        assertEquals(components[0], remaining.subList(0, 4))
        assertEquals(components[1], remaining.subList(4, 8))
        assertEquals(components[2], remaining.subList(8, 12))
        assertEquals(components[3], remaining.subList(12, 16))
    }

    @Test
    fun testDecode() {
        var decoded = ShortVec.decodeLen(listOf(0)).first
        assertEquals(0, decoded)

        decoded = ShortVec.decodeLen(listOf(5).toByteList()).first
        assertEquals(5, decoded)

        decoded = ShortVec.decodeLen(listOf(0x7f).toByteList()).first
        assertEquals(0x7f, decoded)

        decoded = ShortVec.decodeLen(listOf(0x80, 0x01).toByteList()).first
        assertEquals(0x80, decoded)

        decoded = ShortVec.decodeLen(listOf(0xff, 0x01).toByteList()).first
        assertEquals(0xff, decoded)

        decoded = ShortVec.decodeLen(listOf(0x80, 0x02).toByteList()).first
        assertEquals(0x100, decoded)

        decoded = ShortVec.decodeLen(listOf(0xff, 0xff, 0x01).toByteList()).first
        assertEquals(0x7fff, decoded)

        decoded = ShortVec.decodeLen(listOf(0x80, 0x80, 0x80, 0x01).toByteList()).first
        assertEquals(0x200000, decoded)
    }

    @Test
    fun testValidity() {
        for (i in 0..1000) {
            val input = ShortVec.encodeLen(i)
            val actual = ShortVec.decodeLen(input)
            assertEquals(i, actual.first)
        }
    }

    @Test
    fun testCrossImplementation() {
        listOf(
            Pair(0x0,    listOf(0x0).toByteList()),
            Pair(0x7f,   listOf(0x7f).toByteList()),
            Pair(0x80,   listOf(0x80, 0x01).toByteList()),
            Pair(0xff,   listOf(0xff, 0x01).toByteList()),
            Pair(0x100,  listOf(0x80, 0x02).toByteList()),
            Pair(0x7fff, listOf(0xff, 0xff, 0x01).toByteList()),
            Pair(0xffff, listOf(0xff, 0xff, 0x03).toByteList()),
        ).forEach { item ->
            val output = ShortVec.encodeLen(item.first)
            assertEquals(item.second.size, output.size)
            assertEquals(item.second, output)
        }
    }
}