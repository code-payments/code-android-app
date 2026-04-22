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

    // --- TransferForSwapWithFee ---

    @Test
    fun transferForSwapWithFeeEncodeStartsWithCommand() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        assertEquals(20.toByte(), ix.encode()[0])
    }

    @Test
    fun transferForSwapWithFeeEncodeLength() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        // 1 byte command + 8 bytes swapAmount + 8 bytes feeAmount + 1 byte bump = 18
        assertEquals(18, ix.encode().size)
    }

    @Test
    fun transferForSwapWithFeeEncodesBump() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 0L, feeAmount = 0L, bump = 42
        )
        assertEquals(42.toByte(), ix.encode().last())
    }

    @Test
    fun transferForSwapWithFeeEncodesAmountsSeparately() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 95_000L, feeAmount = 5_000L, bump = 1
        )
        val encoded = ix.encode()

        // swapAmount = 95_000 at bytes [1..8] in LE
        assertEquals(0x18.toByte(), encoded[1])
        assertEquals(0x73.toByte(), encoded[2])
        assertEquals(0x01.toByte(), encoded[3])
        for (i in 4..8) assertEquals(0x00.toByte(), encoded[i])

        // feeAmount = 5_000 at bytes [9..16] in LE
        assertEquals(0x88.toByte(), encoded[9])
        assertEquals(0x13.toByte(), encoded[10])
        for (i in 11..16) assertEquals(0x00.toByte(), encoded[i])
    }

    @Test
    fun transferForSwapWithFeeInstructionHasCorrectProgram() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        assertEquals(VirtualMachineProgram.address, ix.instruction().program)
    }

    @Test
    fun transferForSwapWithFeeInstructionHas8Accounts() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        assertEquals(8, ix.instruction().accounts.size)
    }

    @Test
    fun transferForSwapWithFeeSigners() {
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = testKey(7),
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        val accounts = ix.instruction().accounts
        assert(accounts[0].isSigner) { "vmAuthority should be signer" }
        assert(accounts[2].isSigner) { "swapper should be signer" }
        assert(!accounts[1].isSigner) { "vm should not be signer" }
    }

    @Test
    fun transferForSwapWithFeeFeeDestinationAccount() {
        val feeDestKey = testKey(7)
        val ix = VirtualMachineProgram_TransferForSwapWithFee(
            vmAuthority = testKey(1), vm = testKey(2),
            swapper = testKey(3), swapPda = testKey(4),
            swapAta = testKey(5), destination = testKey(6),
            feeDestination = feeDestKey,
            swapAmount = 1000L, feeAmount = 500L, bump = 255
        )
        val accounts = ix.instruction().accounts
        assertEquals(feeDestKey, accounts[6].publicKey)
        assert(accounts[6].isWritable) { "feeDestination should be writable" }
        assert(!accounts[6].isSigner) { "feeDestination should not be signer" }
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
