package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

internal class UsdfProgram_Swap(
    private val amount: Long,
    private val usdfToOther: Boolean,

    private val user: PublicKey,
    private val pool: PublicKey,
    private val usdfVault: PublicKey,
    private val otherVault: PublicKey,
    private val userUsdfToken: PublicKey,
    private val userOtherToken: PublicKey,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = UsdfProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = user, signer = true),

                AccountMeta.readonly(publicKey = pool),

                AccountMeta.writable(publicKey = usdfVault),
                AccountMeta.writable(publicKey = otherVault),
                AccountMeta.writable(publicKey = userUsdfToken),
                AccountMeta.writable(publicKey = userOtherToken),

                AccountMeta.readonly(publicKey = TokenProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> = buildList {
        add(UsdfProgram.Command.swap.value)
        addAll(amount.bytes)
        add(if (usdfToOther) 1 else 0)
    }
}