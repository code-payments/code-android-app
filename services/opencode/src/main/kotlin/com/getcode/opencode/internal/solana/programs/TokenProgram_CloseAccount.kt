package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey

internal class TokenProgram_CloseAccount(
    private val account: PublicKey,
    private val destination: PublicKey,
    private val owner: PublicKey,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = TokenProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = account),
                AccountMeta.writable(publicKey = destination),
                AccountMeta.writable(publicKey = owner),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(TokenProgram.Command.closeAccount.value)
        return data
    }
}