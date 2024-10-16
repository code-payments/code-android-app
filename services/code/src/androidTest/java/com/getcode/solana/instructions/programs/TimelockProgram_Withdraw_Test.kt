package com.getcode.solana.instructions.programs

import com.getcode.mocks.SolanaTransaction
import com.getcode.solana.keys.base58
import com.getcode.utils.DataSlice.byteToUnsignedInt
import junit.framework.Assert.assertEquals
import org.junit.Test

class TimelockProgram_Withdraw_Test {

    @Test
    fun testDecode() {
        val (transaction, _) = SolanaTransaction.mockCloseDormantAccount()
        val rawInstruction = transaction.message.instructions[4]
        val instruction = TimelockProgram_Withdraw.newInstance(rawInstruction)

        assertEquals("FYo8wnNMXhQNy2pV4cC35ZXspBQ3TaERGKDkzwBvGM4r", instruction.timelock.base58())
        assertEquals("EKTfBuyKkhPcvzM7rzKVNCxfj5qeiUwVYLtSrB5XQZ4d", instruction.vault.base58())
        assertEquals("Ed3GWPEdMiRXDMf7jU46fRwBF7n6ZZFGN3vH1dYAgME2", instruction.vaultOwner.base58())
        assertEquals("GEaVZeZ52Jn8xHPy4VKaXsHQ34E6pwfJGuYh8EsYQi6M", instruction.destination.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.payer.base58())
        assertEquals(255, instruction.bump.byteToUnsignedInt())
    }
}