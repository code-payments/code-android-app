package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemProgramTest {

    private fun key(seed: Byte) = PublicKey(ByteArray(32) { seed }.toList())

    @Test
    fun advanceNonceRoundtrip() {
        val original = SystemProgram_AdvanceNonce(nonce = key(1), authority = key(2))
        val decoded = SystemProgram_AdvanceNonce.newInstance(original.instruction())
        assertEquals(original.nonce, decoded.nonce)
        assertEquals(original.authority, decoded.authority)
    }

    @Test
    fun advanceNonceEncodeLength() {
        val inst = SystemProgram_AdvanceNonce(key(1), key(2))
        // 4 bytes Int command
        assertEquals(4, inst.encode().size)
    }

    @Test
    fun advanceNonceCommandByteValue() {
        val inst = SystemProgram_AdvanceNonce(key(1), key(2))
        val encoded = inst.encode()
        // advanceNonceAccount ordinal is 4 → LE bytes [4, 0, 0, 0]
        assertEquals(4, encoded[0].toInt())
        assertEquals(0, encoded[1].toInt())
        assertEquals(0, encoded[2].toInt())
        assertEquals(0, encoded[3].toInt())
    }
}
