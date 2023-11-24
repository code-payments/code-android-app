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

        assertEquals("CaAEvUDhHS5mWZaEFMxBtujusrh3YrRAaC3qx66wNrJz", instruction.timelock.base58())
        assertEquals("Dhqi9xsNZje8U6hFHgqjJZPdf6DifkwSs53sePn5YfQS", instruction.vault.base58())
        assertEquals("HFj1qUaupNsV6MmVpTiG5gnwqJgr4HozcCJqeaWByBYG", instruction.vaultOwner.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.timeAuthority.base58())
        assertEquals("8w2u9wuXfcLbMikp2oNNdvNXVg3odeX7YbuRANECdoyn", instruction.destination.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.payer.base58())
        assertEquals(251, instruction.bump.byteToUnsignedInt())
        assertEquals(Kin.fromQuarks(100000), instruction.kin)
    }
}