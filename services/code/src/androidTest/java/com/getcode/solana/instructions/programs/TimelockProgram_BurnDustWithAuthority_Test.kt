package com.getcode.solana.instructions.programs

import com.getcode.mocks.SolanaTransaction
import com.getcode.solana.keys.base58
import com.getcode.utils.DataSlice.byteToUnsignedInt
import junit.framework.Assert.assertEquals
import org.junit.Test

class TimelockProgram_BurnDustWithAuthority_Test {

    @Test
    fun testDecode() {
        val (transaction, _) = SolanaTransaction.mockCloseEmptyAccount()
        val rawInstruction = transaction.message.instructions[1]
        val instruction = TimelockProgram_BurnDustWithAuthority.newInstance(rawInstruction)

        assertEquals("HzjXkhAQTEffQfXVwCW3yYJ6RbbJToXEDjnfaFZg7e9R", instruction.timelock.base58())
        assertEquals("8V9ioABwqNLsidtRdSWqjJZPxqzKCh6vVqxZWxoSVMb", instruction.vault.base58())
        assertEquals("CiMF8M1VD8HYbWHoX3BhKk4XDcLgzpvz4QJsdULWU84", instruction.vaultOwner.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.timeAuthority.base58())
        assertEquals("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6", instruction.mint.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.payer.base58())
        assertEquals(255, instruction.bump.byteToUnsignedInt())
        assertEquals(1.0, instruction.maxAmount.toKinValueDouble(), 0.0)
    }
}