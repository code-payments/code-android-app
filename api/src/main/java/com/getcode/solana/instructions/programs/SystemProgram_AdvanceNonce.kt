package com.getcode.solana.instructions.programs

import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey
import org.kin.sdk.base.tools.intToByteArray

class SystemProgram_AdvanceNonce(
    val nonce: PublicKey,
    val authority: PublicKey,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = SystemProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = nonce),
                AccountMeta.readonly(publicKey = SysVar.recentBlockhashes.address()),
                AccountMeta.readonly(publicKey = authority, signer = true),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(SystemProgram.Companion.Command.advanceNonceAccount.ordinal.intToByteArray().toList())
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): SystemProgram_AdvanceNonce {
            return SystemProgram_AdvanceNonce(
                nonce = instruction.accounts[0].publicKey,
                authority = instruction.accounts[2].publicKey
            )

        }
    }
}