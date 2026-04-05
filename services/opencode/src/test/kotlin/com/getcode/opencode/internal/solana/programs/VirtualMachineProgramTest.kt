package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualMachineProgramTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    // --- VirtualMachineProgram ---

    @Test
    fun commandTransferForSwapValue() {
        assertEquals(17.toByte(), VirtualMachineProgram.Command.transferForSwap.value)
    }

    @Test
    fun commandCloseSwapValue() {
        assertEquals(19.toByte(), VirtualMachineProgram.Command.closeSwapAccountIfEmpty.value)
    }

    // --- TransferForSwap ---

    @Test
    fun transferForSwapEncodeStartsWithCommand() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 1000L, bump = 255
        )
        val encoded = ix.encode()
        assertEquals(17.toByte(), encoded[0])
    }

    @Test
    fun transferForSwapEncodeLength() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 1000L, bump = 255
        )
        // 1 byte command + 8 bytes amount + 1 byte bump = 10
        assertEquals(10, ix.encode().size)
    }

    @Test
    fun transferForSwapEncodesBump() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 0L, bump = 42
        )
        val encoded = ix.encode()
        assertEquals(42.toByte(), encoded.last())
    }

    @Test
    fun transferForSwapInstructionHasCorrectProgram() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 1000L, bump = 255
        )
        assertEquals(VirtualMachineProgram.address, ix.instruction().program)
    }

    @Test
    fun transferForSwapInstructionHas7Accounts() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 1000L, bump = 255
        )
        assertEquals(7, ix.instruction().accounts.size)
    }

    @Test
    fun transferForSwapSigners() {
        val ix = VirtualMachineProgram_TransferForSwap(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            amount = 1000L, bump = 255
        )
        val accounts = ix.instruction().accounts
        // vmAuthority and swapper should be signers
        assert(accounts[0].isSigner) { "vmAuthority should be signer" }
        assert(accounts[2].isSigner) { "swapper should be signer" }
        assert(!accounts[1].isSigner) { "vm should not be signer" }
    }

    // --- CloseSwapAccountIfEmpty ---

    @Test
    fun closeSwapEncodeStartsWithCommand() {
        val ix = VirtualMachineProgram_CloseSwapAccountIfEmpty(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            bump = 255
        )
        assertEquals(19.toByte(), ix.encode()[0])
    }

    @Test
    fun closeSwapEncodeLength() {
        val ix = VirtualMachineProgram_CloseSwapAccountIfEmpty(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            bump = 255
        )
        // 1 byte command + 1 byte bump = 2
        assertEquals(2, ix.encode().size)
    }

    @Test
    fun closeSwapInstructionHas7Accounts() {
        val ix = VirtualMachineProgram_CloseSwapAccountIfEmpty(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            bump = 255
        )
        assertEquals(7, ix.instruction().accounts.size)
    }
}
