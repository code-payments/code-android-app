package com.getcode.models

import com.getcode.model.Kin
import org.junit.Assert.*
import org.junit.Test

class KinTests {
    @Test
    fun testInitWithKin() {
        assertEquals(100_000, Kin.fromKin(kin = 1).quarks)

        assertEquals(133_334, Kin.fromKin(kin =  1 / 3.0 + 1).quarks)
        assertEquals(166_667, Kin.fromKin(kin =  2 / 3.0 + 1).quarks)

        assertEquals(100_000, Kin.fromKin(kin =  1 / 3 + 1).quarks)
        assertEquals(100_000, Kin.fromKin(kin =  2 / 3 + 1).quarks)
    }

    @Test
    fun testInitNegativeKin() {
        assertThrows(IllegalStateException::class.java) {
            Kin.fromKin(kin = -1)
        }
        assertThrows(IllegalStateException::class.java) {
            Kin(-1)
        }

        assertNotNull(Kin.fromKin(kin = -0))
        assertNotNull(Kin(-0))
    }

    @Test
    fun testInitWithQuarks() {
        assertEquals(100_000, Kin(100_000).quarks)
        assertEquals(133_334, Kin(133_334).quarks)
        assertEquals(166_667, Kin(166_667).quarks)
    }

    @Test
    fun testKinValues() {
        assertEquals(1, Kin(100_000).toKinTruncatingLong())
        assertEquals(1, Kin(133_334).toKinTruncatingLong())
        assertEquals(1, Kin(166_667).toKinTruncatingLong())
    }

    /*fun testComparison() {
        assertTrue(Kin(13) > Kin(12))
        assertTrue(Kin(11) < Kin(12))
        assertTrue(Kin(13) >= Kin(13))
        assertTrue(Kin(14) >= Kin(13))
        assertTrue(Kin(12) <= Kin(12))
        assertTrue(Kin(11) <= Kin(12))
    }*/

    @Test
    fun testTruncation() {
        assertEquals(100_000, Kin.fromQuarks(quarks = 100_000).toKinTruncating().quarks)
        assertEquals(100_000, Kin.fromQuarks(quarks = 133_334).toKinTruncating().quarks)
        assertEquals(100_000, Kin.fromQuarks(quarks = 166_667).toKinTruncating().quarks)
    }

    @Test
    fun testFractional() {
        assertEquals(0, Kin.fromQuarks(quarks = 100_000).fractionalQuarks().quarks)
        assertEquals(33_334, Kin.fromQuarks(quarks = 133_334).fractionalQuarks().quarks)
        assertEquals(66_667, Kin.fromQuarks(quarks = 166_667).fractionalQuarks().quarks)
    }

    @Test
    fun testInflation() {
        assertEquals(100_000, Kin.fromQuarks(quarks = 99_999).inflating().quarks)
        assertEquals(100_000, Kin.fromQuarks(quarks = 100_000).inflating().quarks)
        assertEquals(200_000, Kin.fromQuarks(quarks = 133_334).inflating().quarks)
        assertEquals(200_000, Kin.fromQuarks(quarks = 166_667).inflating().quarks)
    }

    @Test
    fun testMultiplication() {
        assertEquals(Kin.fromKin(kin = 123_000), Kin.fromKin(kin = 123) * 1_000)
        assertEquals(Kin.fromKin(kin = 123), Kin.fromKin(kin = 123) * 1)
        assertEquals(Kin.fromKin(kin = 0), Kin.fromKin(kin = 123) * 0)
    }

    @Test
    fun testDivision() {
        assertEquals(10_000.0, Kin.fromKin(kin = 100_000 / 10).toKin().toDouble(), 0.0)
        assertEquals(12.0, Kin.fromKin(kin = 123 / 10).toKin().toDouble(), 0.0)
        assertEquals(123.0, Kin.fromKin(kin =  123 / 1).toKin().toDouble(), 0.0)
    }
}