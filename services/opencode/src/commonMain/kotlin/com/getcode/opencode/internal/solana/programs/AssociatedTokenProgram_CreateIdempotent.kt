package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey

internal class AssociatedTokenProgram_CreateIdempotent(
    private val subsidizer: PublicKey,
    private val owner: PublicKey,
    private val mint: PublicKey,
) : InstructionType {

    val address: PublicKey = PublicKey.deriveAssociatedAccount(owner = owner, mint = mint).publicKey

    override fun instruction(): Instruction {
        return Instruction(
            program = AssociatedTokenProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = subsidizer, signer = true),
                AccountMeta.writable(publicKey = address),
                AccountMeta.readonly(publicKey = owner),
                AccountMeta.readonly(publicKey = mint),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(AssociatedTokenProgram.Command.createIdempotent.value)
        return data
    }
}