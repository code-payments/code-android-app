package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

@Suppress("ClassName")
internal class CurrencyCreatorProgram_SellTokens(
    private val amount: Long,
    private val minOutput: Long,
    private val seller: PublicKey,
    private val pool: PublicKey,
    private val currency: PublicKey,
    private val targetMint: PublicKey,
    private val baseMint: PublicKey,
    private val vaultTarget: PublicKey,
    private val vaultBase: PublicKey,
    private val sellerTarget: PublicKey,
    private val sellerBase: PublicKey,
    private val feeTarget: PublicKey,
    private val feeBase: PublicKey,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = CurrencyCreatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = seller, signer = true),
                AccountMeta.writable(publicKey = pool),
                AccountMeta.writable(publicKey = currency),
                AccountMeta.writable(publicKey = targetMint),

                AccountMeta.readonly(publicKey = baseMint),

                AccountMeta.writable(publicKey = vaultTarget),
                AccountMeta.writable(publicKey = vaultBase),
                AccountMeta.writable(publicKey = sellerTarget),
                AccountMeta.writable(publicKey = sellerBase),

                AccountMeta.readonly(publicKey = feeTarget),

                AccountMeta.writable(publicKey = feeBase),

                AccountMeta.readonly(publicKey = TokenProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(CurrencyCreatorProgram.Command.sellTokens.value)
        data.addAll(amount.bytes)
        data.addAll(minOutput.bytes)

        return data
    }
}