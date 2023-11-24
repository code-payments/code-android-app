package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_revokelockwithauthority.go
class TimelockProgram_RevokeLockWithAuthority(
    val timelock: PublicKey,
    val vault: PublicKey,
    val closeAuthority: PublicKey,
    val payer: PublicKey,
    val bump: Byte,
    val legacy: Boolean = false,
): InstructionType {
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
        data.addAll(TimelockProgram.Companion.Command.revokeLockWithAuthority.value.toByteArray().toList())
        data.add(bump)

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_RevokeLockWithAuthority {
            val data = TimelockProgram.parse(instruction = instruction, expectingAccounts = 6)

            val bump = data.consume(1)

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