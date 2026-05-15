package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey

@Suppress("ClassName")
internal class VirtualMachineProgram_InitVm(
    private val vmAuthority: PublicKey,
    private val vm: PublicKey,
    private val vmOmnibus: PublicKey,
    private val mint: PublicKey,
    private val lockDuration: Int, // days, 1 byte
    private val vmBump: Int,
    private val vmOmnibusBump: Int,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = VirtualMachineProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = vmAuthority, signer = true),
                AccountMeta.writable(publicKey = vm),
                AccountMeta.writable(publicKey = vmOmnibus),
                AccountMeta.readonly(publicKey = mint),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(VirtualMachineProgram.Command.initVm.value)
        data.add(lockDuration.toByte())
        data.add(vmBump.toByte())
        data.add(vmOmnibusBump.toByte())

        return data
    }
}
