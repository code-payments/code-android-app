package com.getcode.solana

import com.getcode.solana.instructions.programs.AssociatedTokenProgram
import com.getcode.solana.instructions.programs.SystemProgram
import com.getcode.solana.instructions.programs.TokenProgram
import com.getcode.solana.keys.base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramAddressTest {

    @Test
    fun systemProgramAddressIsAllZeros() {
        val bytes = SystemProgram.address.bytes
        assertEquals(32, bytes.size)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun tokenProgramAddressBase58() {
        assertEquals(
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            TokenProgram.address.base58(),
        )
    }

    @Test
    fun associatedTokenProgramAddressBase58() {
        assertEquals(
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
            AssociatedTokenProgram.address.base58(),
        )
    }

    @Test
    fun systemProgramCommandEnumHas12Values() {
        assertEquals(12, SystemProgram.Command.entries.size)
    }

    @Test
    fun systemProgramCommandContainsExpectedValues() {
        val commands = SystemProgram.Command.entries.map { it.name }
        assertTrue("createAccount" in commands)
        assertTrue("assign" in commands)
        assertTrue("transfer" in commands)
        assertTrue("createAccountWithSeed" in commands)
        assertTrue("advanceNonceAccount" in commands)
        assertTrue("withdrawNonceAccount" in commands)
        assertTrue("initializeNonceAccount" in commands)
        assertTrue("authorizeNonceAccount" in commands)
        assertTrue("allocate" in commands)
        assertTrue("allocateWithSeed" in commands)
        assertTrue("assignWithSeed" in commands)
        assertTrue("transferWithSeed" in commands)
    }
}
