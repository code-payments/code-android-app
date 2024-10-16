package com.getcode.solana.instructions.programs

import com.getcode.mocks.SolanaTransaction
import com.getcode.solana.keys.base58
import junit.framework.Assert.assertEquals
import org.junit.Test

class TimelockProgram_Initialize_Test {

    @Test
    fun testDecode() {
        val (transaction, _) = SolanaTransaction.mockTimelockCreateAccount()
        val rawInstruction = transaction.message.instructions[1]
        val instruction = TimelockProgram_Initialize.newInstance(rawInstruction)

        assertEquals("11111111111111111111111111111111", instruction.nonce.base58())
        assertEquals("DhvyJ6DsJTUsuhCTy8UzBj4r4nREadG6Cx4HCyiGPQJ1", instruction.timelock.base58())
        assertEquals("Jy9M4nEwwfeiteamfJ3BN75p45e4tJEaR3xcYh1NtB5", instruction.vault.base58())
        assertEquals("55nFdnZsTaQUEcRiT4CRuTKCduvpDPWf5VKaPpup6Pus", instruction.vaultOwner.base58())
        assertEquals("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6", instruction.mint.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.timeAuthority.base58())
        assertEquals("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR", instruction.payer.base58())
        assertEquals(1814400, instruction.lockout)
    }
}