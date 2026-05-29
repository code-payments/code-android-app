package com.flipcash.app.contacts.sync

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactChecksumTest {

    @Test
    fun `empty set produces zero checksum`() {
        val bytes = ContactChecksum.computeBytes(emptySet())
        assertEquals(32, bytes.size)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `single entry produces non-zero checksum`() {
        val bytes = ContactChecksum.computeBytes(setOf("+15551234567"))
        assertEquals(32, bytes.size)
        assertTrue(bytes.any { it != 0.toByte() })
    }

    @Test
    fun `order does not matter`() {
        val a = ContactChecksum.computeBytes(setOf("+15551234567", "+15559876543"))
        val b = ContactChecksum.computeBytes(setOf("+15559876543", "+15551234567"))
        assertContentEquals(a, b)
    }

    @Test
    fun `same input is deterministic`() {
        val phones = setOf("+15551234567", "+15559876543", "+447911123456")
        val a = ContactChecksum.computeBytes(phones)
        val b = ContactChecksum.computeBytes(phones)
        assertContentEquals(a, b)
    }

    @Test
    fun `different inputs produce different checksums`() {
        val a = ContactChecksum.computeBytes(setOf("+15551234567"))
        val b = ContactChecksum.computeBytes(setOf("+15559876543"))
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `adding a contact changes checksum`() {
        val base = setOf("+15551234567", "+15559876543")
        val extended = base + "+447911123456"

        val a = ContactChecksum.computeBytes(base)
        val b = ContactChecksum.computeBytes(extended)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `removing a contact changes checksum`() {
        val full = setOf("+15551234567", "+15559876543", "+447911123456")
        val reduced = full - "+15559876543"

        val a = ContactChecksum.computeBytes(full)
        val b = ContactChecksum.computeBytes(reduced)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `XOR self-cancellation property`() {
        val singleA = ContactChecksum.computeBytes(setOf("+15551234567"))
        val singleB = ContactChecksum.computeBytes(setOf("+15559876543"))
        val both = ContactChecksum.computeBytes(setOf("+15551234567", "+15559876543"))

        // XOR(both, singleB) should equal singleA
        val result = ByteArray(32)
        for (i in result.indices) {
            result[i] = (both[i].toInt() xor singleB[i].toInt()).toByte()
        }
        assertContentEquals(singleA, result)
    }
}
