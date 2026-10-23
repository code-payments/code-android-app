package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

internal class UsdfProgram_Transfer(
    private val amount: Long,
    private val isUsdf: Boolean,

    private val authority: PublicKey,
    private val pool: PublicKey,
    private val vault: PublicKey,
    private val destination: PublicKey,
): InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = UsdfProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = authority, signer = true),

                AccountMeta.readonly(publicKey = pool),

                AccountMeta.writable(publicKey = vault),
                AccountMeta.writable(publicKey = destination),

                AccountMeta.readonly(publicKey = TokenProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> = buildList {
        add(UsdfProgram.Command.swap.value)
        addAll(amount.bytes)
        add(if (isUsdf) 1 else 0)
    }
}