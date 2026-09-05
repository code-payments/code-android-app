package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DerivePathTest {

    @Test
    fun parsePrimaryPath() {
        val path = DerivePath.newInstance("m/44'/501'/0'/0'")
        assertNotNull(path)
        assertEquals(4, path.indexes.size)
        assertEquals(44, path.indexes[0].value)
        assertTrue(path.indexes[0].hardened)
        assertEquals(501, path.indexes[1].value)
        assertEquals(0, path.indexes[2].value)
        assertEquals(0, path.indexes[3].value)
    }

    @Test
    fun primaryPathMatchesPredefined() {
        val parsed = DerivePath.newInstance("m/44'/501'/0'/0'")
        assertEquals(DerivePath.primary, parsed)
    }

    @Test
    fun stringRepresentationRoundtrip() {
        val original = "m/44'/501'/0'/0'"
        val path = DerivePath.newInstance(original)
        assertNotNull(path)
        assertEquals(original, path.stringRepresentation())
    }

    @Test
    fun parseNonHardenedIndexes() {
        val path = DerivePath.newInstance("m/44/501/0/0")
        assertNotNull(path)
        assertEquals(4, path.indexes.size)
        path.indexes.forEach { assertFalse(it.hardened) }
    }

    @Test
    fun parseMixedHardenedIndexes() {
        val path = DerivePath.newInstance("m/44'/501/0'/0")
        assertNotNull(path)
        assertTrue(path.indexes[0].hardened)
        assertFalse(path.indexes[1].hardened)
        assertTrue(path.indexes[2].hardened)
        assertFalse(path.indexes[3].hardened)
    }

    @Test
    fun invalidPathNoIdentifier() {
        val path = DerivePath.newInstance("x/44'/501'/0'/0'")
        assertNull(path)
    }

    @Test
    fun invalidPathNonNumericIndex() {
        val path = DerivePath.newInstance("m/abc/501'/0'/0'")
        assertNull(path)
    }

    @Test
    fun emptyPathAfterIdentifier() {
        val path = DerivePath.newInstance("m")
        assertNotNull(path)
        assertEquals(0, path.indexes.size)
    }

    @Test
    fun bucketPaths() {
        assertEquals("m/44'/501'/0'/0'/0'/1", DerivePath.bucket1.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/10", DerivePath.bucket10.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/100", DerivePath.bucket100.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/1000", DerivePath.bucket1k.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/10000", DerivePath.bucket10k.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/100000", DerivePath.bucket100k.stringRepresentation())
        assertEquals("m/44'/501'/0'/0'/0'/1000000", DerivePath.bucket1m.stringRepresentation())
    }

    @Test
    fun swapPath() {
        assertEquals("m/44'/501'/0'/0'/1'/0", DerivePath.swap.stringRepresentation())
    }

    @Test
    fun getBucketIncoming() {
        val path = DerivePath.getBucketIncoming(5)
        assertEquals("m/44'/501'/0'/0'/5'/2", path.stringRepresentation())
    }

    @Test
    fun getBucketOutgoing() {
        val path = DerivePath.getBucketOutgoing(5)
        assertEquals("m/44'/501'/0'/0'/5'/3", path.stringRepresentation())
    }

    @Test
    fun getPool() {
        val path = DerivePath.getPool(42)
        assertEquals("m/44'/501'/0'/0'/7665'/42'", path.stringRepresentation())
    }

    @Test
    fun getPoolRendezvous() {
        val path = DerivePath.getPoolRendezvous(7)
        assertEquals("m/44'/501'/0'/0'/2335'/7'", path.stringRepresentation())
    }

    @Test
    fun equalityByIndexes() {
        val a = DerivePath.newInstance("m/44'/501'/0'/0'")
        val b = DerivePath.newInstance("m/44'/501'/0'/0'")
        assertEquals(a, b)
    }

    @Test
    fun passwordPreserved() {
        val path = DerivePath.newInstance("m/44'/501'/0'/0'/0'/0", password = "example.com")
        assertNotNull(path)
        assertEquals("example.com", path.password)
    }

    @Test
    fun passwordNotAffectEquality() {
        val a = DerivePath.newInstance("m/44'/501'/0'/0'", password = "a")
        val b = DerivePath.newInstance("m/44'/501'/0'/0'", password = "b")
        assertEquals(a, b)
    }

    private fun assertFalse(value: Boolean) = kotlin.test.assertFalse(value)
}
