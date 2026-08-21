package com.flipcash.services.user

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsIdentityTest {

    // iOS Data.hexEncodedString(): lowercase, two zero-padded digits per byte,
    // no separators, no 0x prefix. These vectors are the cross-platform contract.
    @Test
    fun `encodes bytes as lowercase unseparated hex`() {
        val id = listOf<Byte>(0x01, 0x02, 0x03)
        assertEquals("010203", id.analyticsDistinctId())
    }

    @Test
    fun `zero-pads single digit bytes`() {
        val id = listOf<Byte>(0x00, 0x0f)
        assertEquals("000f", id.analyticsDistinctId())
    }

    @Test
    fun `encodes high bytes as lowercase without sign extension`() {
        val id = listOf<Byte>(0xff.toByte(), 0xab.toByte())
        assertEquals("ffab", id.analyticsDistinctId())
    }

    @Test
    fun `empty id encodes to empty string`() {
        assertEquals("", emptyList<Byte>().analyticsDistinctId())
    }
}
