package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.toByteArray

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_revokelockwithauthority.go
class TimelockProgram_RevokeLockWithAuthority(
    val timelock: com.getcode.solana.keys.PublicKey,
    val vault: com.getcode.solana.keys.PublicKey,
    val closeAuthority: com.getcode.solana.keys.PublicKey,
    val payer: com.getcode.solana.keys.PublicKey,
    val bump: Byte,
    val legacy: Boolean = false,
) : com.getcode.solana.instructions.InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = if (legacy) TimelockProgram.legacyAddress else TimelockProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = timelock),
                AccountMeta.writable(publicKey = vault),
                AccountMeta.readonly(publicKey = closeAuthority, signer = true),
                AccountMeta.writable(publicKey = payer, signer = true),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(Command.revokeLockWithAuthority.value.toByteArray().toList())
        data.add(bump)

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_RevokeLockWithAuthority {
            val data = TimelockProgram.parse(
                command = Command.revokeLockWithAuthority,
                instruction = instruction,
                expectingAccounts = 6
            )


            val bump = data.remaining.consume(1)

            return TimelockProgram_RevokeLockWithAuthority(
                timelock = instruction.accounts[0].publicKey,
                vault = instruction.accounts[1].publicKey,
                closeAuthority = instruction.accounts[2].publicKey,
                payer = instruction.accounts[3].publicKey,
                bump = bump.consumed.first()
            )
        }
    }
}