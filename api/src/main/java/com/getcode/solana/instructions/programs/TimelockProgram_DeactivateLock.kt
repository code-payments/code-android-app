package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_deactivate.go
class TimelockProgram_DeactivateLock(
    val timelock: PublicKey,
    val vaultOwner: PublicKey,
    val payer: PublicKey,
    val bump: Byte,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = TimelockProgram.address,
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