package com.getcode.solana.instructions.programs

import com.getcode.model.Kin
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelockProgramTest {

    private fun key(seed: Byte) = PublicKey(ByteArray(32) { seed }.toList())

    // --- Initialize ---

    @Test
    fun initializeRoundtrip() {
        val original = TimelockProgram_Initialize(
            nonce = key(1), timelock = key(2), vault = key(3), vaultOwner = key(4),
            mint = key(5), timeAuthority = key(6), payer = key(7), lockout = 86400L,
        )
        val decoded = TimelockProgram_Initialize.newInstance(original.instruction())
        assertEquals(86400L, decoded.lockout)
        assertEquals(original.nonce, decoded.nonce)
        assertEquals(original.timelock, decoded.timelock)
        assertEquals(original.vault, decoded.vault)
    }

    @Test
    fun initializeLockoutZero() {
        val original = TimelockProgram_Initialize(
            nonce = key(1), timelock = key(2), vault = key(3), vaultOwner = key(4),
            mint = key(5), timeAuthority = key(6), payer = key(7), lockout = 0L,
        )
        val decoded = TimelockProgram_Initialize.newInstance(original.instruction())
        assertEquals(0L, decoded.lockout)
    }

    @Test
    fun initializeEncodeLength() {
        val inst = TimelockProgram_Initialize(
            key(1), key(2), key(3), key(4), key(5), key(6), key(7), lockout = 1L,
        )
        // 8 bytes command + 8 bytes lockout
        assertEquals(16, inst.encode().size)
    }

    // --- TransferWithAuthority ---

    @Test
    fun transferWithAuthorityRoundtrip() {
        val original = TimelockProgram_TransferWithAuthority(
            timelock = key(1), vault = key(2), vaultOwner = key(3), timeAuthority = key(4),
            destination = key(5), payer = key(6), bump = 42, quarks = 1_000_000L,
        )
        val decoded = TimelockProgram_TransferWithAuthority.newInstance(original.instruction())
        assertEquals(42.toByte(), decoded.bump)
        assertEquals(1_000_000L, decoded.quarks)
        assertEquals(original.timelock, decoded.timelock)
        assertEquals(original.destination, decoded.destination)
    }

    @Test
    fun transferWithAuthorityZeroQuarks() {
        val original = TimelockProgram_TransferWithAuthority(
            timelock = key(1), vault = key(2), vaultOwner = key(3), timeAuthority = key(4),
            destination = key(5), payer = key(6), bump = 0, quarks = 0L,
        )
        val decoded = TimelockProgram_TransferWithAuthority.newInstance(original.instruction())
        assertEquals(0.toByte(), decoded.bump)
        assertEquals(0L, decoded.quarks)
    }

    @Test
    fun transferWithAuthorityEncodeLength() {
        val inst = TimelockProgram_TransferWithAuthority(
            key(1), key(2), key(3), key(4), key(5), key(6), bump = 1, quarks = 1L,
        )
        // 8 bytes command + 1 byte bump + 8 bytes quarks
        assertEquals(17, inst.encode().size)
    }

    // --- Withdraw ---

    @Test
    fun withdrawRoundtrip() {
        val original = TimelockProgram_Withdraw(
            timelock = key(1), vault = key(2), vaultOwner = key(3),
            destination = key(4), payer = key(5), bump = 7,
        )
        val decoded = TimelockProgram_Withdraw.newInstance(original.instruction())
        assertEquals(7.toByte(), decoded.bump)
        assertEquals(original.vault, decoded.vault)
        assertEquals(original.destination, decoded.destination)
    }

    @Test
    fun withdrawEncodeLength() {
        val inst = TimelockProgram_Withdraw(key(1), key(2), key(3), key(4), key(5), bump = 1)
        // 8 bytes command + 1 byte bump
        assertEquals(9, inst.encode().size)
    }

    // --- CloseAccounts ---

    @Test
    fun closeAccountsRoundtrip() {
        val original = TimelockProgram_CloseAccounts(
            timelock = key(1), vault = key(2), closeAuthority = key(3),
            payer = key(4), bump = 3,
        )
        val decoded = TimelockProgram_CloseAccounts.newInstance(original.instruction())
        assertEquals(3.toByte(), decoded.bump)
        assertEquals(original.closeAuthority, decoded.closeAuthority)
    }

    @Test
    fun closeAccountsEncodeLength() {
        val inst = TimelockProgram_CloseAccounts(key(1), key(2), key(3), key(4), bump = 1)
        assertEquals(9, inst.encode().size)
    }

    // --- DeactivateLock ---

    @Test
    fun deactivateLockRoundtrip() {
        val original = TimelockProgram_DeactivateLock(
            timelock = key(1), vaultOwner = key(2), payer = key(3), bump = 99.toByte(),
        )
        val decoded = TimelockProgram_DeactivateLock.newInstance(original.instruction())
        assertEquals(99.toByte(), decoded.bump)
        assertEquals(original.timelock, decoded.timelock)
    }

    @Test
    fun deactivateLockEncodeLength() {
        val inst = TimelockProgram_DeactivateLock(key(1), key(2), key(3), bump = 1)
        assertEquals(9, inst.encode().size)
    }

    // --- RevokeLockWithAuthority ---

    @Test
    fun revokeLockRoundtrip() {
        val original = TimelockProgram_RevokeLockWithAuthority(
            timelock = key(1), vault = key(2), closeAuthority = key(3),
            payer = key(4), bump = 5,
        )
        val decoded = TimelockProgram_RevokeLockWithAuthority.newInstance(original.instruction())
        assertEquals(5.toByte(), decoded.bump)
        assertEquals(original.vault, decoded.vault)
    }

    @Test
    fun revokeLockEncodeLength() {
        val inst = TimelockProgram_RevokeLockWithAuthority(key(1), key(2), key(3), key(4), bump = 1)
        assertEquals(9, inst.encode().size)
    }

    // --- BurnDustWithAuthority ---

    @Test
    fun burnDustRoundtrip() {
        val maxAmount = Kin.fromQuarks(500_000L)
        val original = TimelockProgram_BurnDustWithAuthority(
            timelock = key(1), vault = key(2), vaultOwner = key(3), timeAuthority = key(4),
            mint = key(5), payer = key(6), bump = 12, maxAmount = maxAmount,
        )
        val decoded = TimelockProgram_BurnDustWithAuthority.newInstance(original.instruction())
        assertEquals(12.toByte(), decoded.bump)
        assertEquals(maxAmount.quarks, decoded.maxAmount.quarks)
    }

    @Test
    fun burnDustZeroAmount() {
        val original = TimelockProgram_BurnDustWithAuthority(
            timelock = key(1), vault = key(2), vaultOwner = key(3), timeAuthority = key(4),
            mint = key(5), payer = key(6), bump = 0, maxAmount = Kin.fromQuarks(0),
        )
        val decoded = TimelockProgram_BurnDustWithAuthority.newInstance(original.instruction())
        assertEquals(0.toByte(), decoded.bump)
        assertEquals(0L, decoded.maxAmount.quarks)
    }

    @Test
    fun burnDustEncodeLength() {
        val inst = TimelockProgram_BurnDustWithAuthority(
            key(1), key(2), key(3), key(4), key(5), key(6),
            bump = 1, maxAmount = Kin.fromQuarks(1),
        )
        // 8 bytes command + 1 byte bump + 8 bytes maxAmount
        assertEquals(17, inst.encode().size)
    }
}
