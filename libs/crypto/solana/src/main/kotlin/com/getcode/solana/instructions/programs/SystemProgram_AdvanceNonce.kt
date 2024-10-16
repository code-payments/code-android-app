package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.Instruction
import org.kin.sdk.base.tools.intToByteArray

class SystemProgram_AdvanceNonce(
    val nonce: com.getcode.solana.keys.PublicKey,
    val authority: com.getcode.solana.keys.PublicKey,
) : com.getcode.solana.instructions.InstructionType {
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
        data.addAll(SystemProgram.Command.advanceNonceAccount.ordinal.intToByteArray().toList())
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