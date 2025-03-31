package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.toByteArray

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_deactivate.go
class TimelockProgram_DeactivateLock(
    val timelock: com.getcode.solana.keys.PublicKey,
    val vaultOwner: com.getcode.solana.keys.PublicKey,
    val payer: com.getcode.solana.keys.PublicKey,
    val bump: Byte,
    val legacy: Boolean = false,
) : com.getcode.solana.instructions.InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = if (legacy) TimelockProgram.legacyAddress else TimelockProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = timelock),
                AccountMeta.readonly(publicKey = vaultOwner, signer = true),
                AccountMeta.writable(publicKey = payer, signer = true),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(Command.deactivateLock.value.toByteArray().toList())
        data.add(bump)

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_DeactivateLock {
            val data = TimelockProgram.parse(
                command = Command.deactivateLock,
                instruction = instruction,
                expectingAccounts = 3
            )


            val bump = data.remaining.consume(1)

            return TimelockProgram_DeactivateLock(
                timelock = instruction.accounts[0].publicKey,
                vaultOwner = instruction.accounts[1].publicKey,
                payer = instruction.accounts[2].publicKey,
                bump = bump.consumed.first()
            )
        }
    }
}