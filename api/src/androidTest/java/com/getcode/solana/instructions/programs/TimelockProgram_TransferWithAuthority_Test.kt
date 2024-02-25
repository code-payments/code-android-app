package com.getcode.solana.instructions.programs

import com.getcode.mocks.SolanaTransaction
import com.getcode.model.Kin
import com.getcode.solana.keys.base58
import com.getcode.utils.DataSlice.byteToUnsignedInt
import junit.framework.Assert.assertEquals
import org.junit.Test

class TimelockProgram_TransferWithAuthority_Test {

    @Test
    fun testDecode() {
        val (transaction, _) = SolanaTransaction.mockTimelockTransfer()
        val rawInstruction = transaction.message.instructions[2]
        val instruction = TimelockProgram_TransferWithAuthority.newInstance(rawInstruction)

        assertEquals("GbhARQ2W8qVgFxE9jSAGTAqeaUuBrczWBd9VvtT5u4MW", instruction.timelock.base58())
        assertEquals("2khXZy3LDvTxf5VcdgLip11ip4FjTr1vUdq2ATLeQE7r", instruction.vault.base58())
        assertEquals("Ddk7k7zMMWsp8fZB12wqbiADdXKQFWfwUUsxSo73JaQ9", instruction.vaultOwner.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.timeAuthority.base58())
        assertEquals("2sDAFcEZkLd3mbm6SaZhifctkyB4NWsp94GMnfDs1BfR", instruction.destination.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.payer.base58())
        assertEquals(255, instruction.bump.byteToUnsignedInt())
        assertEquals(Kin.fromKin(2), instruction.kin)
    }
}