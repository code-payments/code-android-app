package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.LENGTH_32
import kotlin.test.Test
import kotlin.test.assertEquals

class SysVarTest {

    @Test
    fun `SysVar enum has 9 entries`() {
        assertEquals(9, SysVar.entries.size)
    }

    @Test
    fun `SysVar rent address is a valid 32-byte PublicKey`() {
        val address = SysVar.rent.address()
        assertEquals(LENGTH_32, address.bytes.size)
    }

    @Test
    fun `SysVar clock address is a valid 32-byte PublicKey`() {
        val address = SysVar.clock.address()
        assertEquals(LENGTH_32, address.bytes.size)
    }
}
