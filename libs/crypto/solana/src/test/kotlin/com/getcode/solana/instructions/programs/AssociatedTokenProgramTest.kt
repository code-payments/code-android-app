package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssociatedTokenProgramTest {

    private fun key(seed: Byte) = PublicKey(ByteArray(32) { seed }.toList())

    @Test
    fun createAccountRoundtrip() {
        val original = AssociatedTokenProgram_CreateAccount(
            subsidizer = key(1), owner = key(2),
            associatedTokenAccount = key(3), mint = key(4),
        )
        val decoded = AssociatedTokenProgram_CreateAccount.newInstance(original.instruction())
        assertEquals(original.subsidizer, decoded.subsidizer)
        assertEquals(original.mint, decoded.mint)
    }

    @Test
    fun createAccountEncodeIsEmpty() {
        val inst = AssociatedTokenProgram_CreateAccount(key(1), key(2), key(3), key(4))
        assertTrue(inst.encode().isEmpty())
    }

    @Test
    fun createAccountInstructionHasSevenAccounts() {
        val inst = AssociatedTokenProgram_CreateAccount(key(1), key(2), key(3), key(4))
        assertEquals(7, inst.instruction().accounts.size)
    }
}
