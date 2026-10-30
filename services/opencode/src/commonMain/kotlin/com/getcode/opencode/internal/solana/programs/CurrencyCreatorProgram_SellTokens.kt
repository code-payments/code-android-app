package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

@Suppress("ClassName")
internal class CurrencyCreatorProgram_SellTokens(
    private val inAmount: Long,
    private val minOutAmount: Long,

    private val seller: PublicKey,
    private val pool: PublicKey,
    private val targetMint: PublicKey,
    private val baseMint: PublicKey,
    private val vaultTarget: PublicKey,
    private val vaultBase: PublicKey,
    private val sellerTarget: PublicKey,
    private val sellerBase: PublicKey,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = CurrencyCreatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = seller, signer = true),
                AccountMeta.writable(publicKey = pool),

                AccountMeta.readonly(publicKey = targetMint),
                AccountMeta.readonly(publicKey = baseMint),

                AccountMeta.writable(publicKey = vaultTarget),
                AccountMeta.writable(publicKey = vaultBase),
                AccountMeta.writable(publicKey = sellerTarget),
                AccountMeta.writable(publicKey = sellerBase),

                AccountMeta.readonly(publicKey = TokenProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(CurrencyCreatorProgram.Command.sellTokens.value)
        data.addAll(inAmount.bytes)
        data.addAll(minOutAmount.bytes)

        return data
    }
}