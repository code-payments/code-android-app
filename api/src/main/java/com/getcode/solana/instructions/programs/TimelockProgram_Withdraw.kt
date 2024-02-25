package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import org.kin.sdk.base.tools.longToByteArray

// Reference: https://github.com/code-wallet/code-server/blob/privacy-v3/pkg/solana/timelock/instruction_withdraw.go
class TimelockProgram_Withdraw(
    val timelock: PublicKey,
    val vault: PublicKey,
    val vaultOwner: PublicKey,
    val destination: PublicKey,
    val payer: PublicKey,
    val bump: Byte,
    val legacy: Boolean = false,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = if (legacy) TimelockProgram.legacyAddress else TimelockProgram.address,
            accounts = listOf(
                AccountMeta.readonly(publicKey = timelock),
                AccountMeta.writable(publicKey = vault),
                AccountMeta.readonly(publicKey = vaultOwner, signer = true),
                AccountMeta.writable(publicKey = destination),
                AccountMeta.writable(publicKey = payer, signer = true),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(Command.withdraw.value.toByteArray().toList())
        data.add(bump)

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_Withdraw {
            val data = TimelockProgram.parse(
                command = Command.withdraw,
                instruction = instruction,
                expectingAccounts = 7
            )

            val bump = data.remaining.consume(1)

            return TimelockProgram_Withdraw(
                timelock = instruction.accounts[0].publicKey,
                vault = instruction.accounts[1].publicKey,
                vaultOwner = instruction.accounts[2].publicKey,
                destination = instruction.accounts[3].publicKey,
                payer = instruction.accounts[4].publicKey,
                bump = bump.consumed.first()
            )
        }
    }
}