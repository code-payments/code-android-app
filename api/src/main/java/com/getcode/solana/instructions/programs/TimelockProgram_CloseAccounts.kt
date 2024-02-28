package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.instructions.programs.TimelockProgram.Command.closeAccounts
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_closeaccounts.go
class TimelockProgram_CloseAccounts(
    val timelock: PublicKey,
    val vault: PublicKey,
    val closeAuthority: PublicKey,
    val payer: PublicKey,
    val bump: Byte,
    val legacy: Boolean = false,
) : InstructionType {
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
        data.addAll(closeAccounts.value.toByteArray().toList())
        data.add(bump)
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_CloseAccounts {
            val data = TimelockProgram.parse(
                command = closeAccounts,
                instruction = instruction,
                expectingAccounts = 6
            )


            val bump = data.remaining.consume(1)

            return TimelockProgram_CloseAccounts(
                timelock = instruction.accounts[0].publicKey,
                vault = instruction.accounts[1].publicKey,
                closeAuthority = instruction.accounts[2].publicKey,
                payer = instruction.accounts[3].publicKey,
                bump = bump.consumed.first()
            )
        }
    }
}