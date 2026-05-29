package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

internal class VirtualMachineProgram_TransferForSwap(
    private val vmAuthority: PublicKey,
    private val vm: PublicKey,
    private val swapper: PublicKey,
    private val swapPda: PublicKey,
    private val swapAta: PublicKey,
    private val destination: PublicKey,
    private val amount: Long,
    private val bump: Int
): InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = VirtualMachineProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = vmAuthority, signer = true),

                AccountMeta.writable(publicKey = vm),

                AccountMeta.writable(publicKey = swapper, signer = true),

                AccountMeta.readonly(publicKey = swapPda),

                AccountMeta.writable(publicKey = swapAta),
                AccountMeta.writable(publicKey = destination),

                AccountMeta.readonly(publicKey = TokenProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(VirtualMachineProgram.Command.transferForSwap.value)
        data.addAll(amount.bytes)
        data.add(bump.toByte())

        return data
    }
}