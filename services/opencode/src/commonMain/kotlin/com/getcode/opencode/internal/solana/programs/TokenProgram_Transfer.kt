package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

class TokenProgram_Transfer(
    private val amount: Long,

    private val source: PublicKey,
    private val destination: PublicKey,
    private val owner: PublicKey,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = TokenProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = source),
                AccountMeta.writable(publicKey = destination),
                AccountMeta.readonly(publicKey = owner, signer = true),

            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> = buildList {
        add(TokenProgram.Command.transfer.value)
        addAll(amount.bytes)
    }
}