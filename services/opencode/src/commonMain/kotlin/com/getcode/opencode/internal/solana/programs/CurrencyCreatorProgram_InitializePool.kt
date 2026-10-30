package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.extensions.toU16Bytes
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey

@Suppress("ClassName")
internal class CurrencyCreatorProgram_InitializePool(
    private val authority: PublicKey,
    private val currency: PublicKey,
    private val targetMint: PublicKey,
    private val baseMint: PublicKey,
    private val pool: PublicKey,
    private val vaultTarget: PublicKey,
    private val vaultBase: PublicKey,
    private val sellFee: Int, // u16
    private val bump: Int,
    private val vaultTargetBump: Int,
    private val vaultBaseBump: Int,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = CurrencyCreatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = authority, signer = true),
                AccountMeta.readonly(publicKey = currency),
                AccountMeta.writable(publicKey = targetMint),
                AccountMeta.readonly(publicKey = baseMint),
                AccountMeta.writable(publicKey = pool),
                AccountMeta.writable(publicKey = vaultTarget),
                AccountMeta.writable(publicKey = vaultBase),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(CurrencyCreatorProgram.Command.initializePool.value)

        // sellFee: u16 LE
        data.addAll(sellFee.toU16Bytes())

        // bump: 1 byte
        data.add(bump.toByte())

        // vaultTargetBump: 1 byte
        data.add(vaultTargetBump.toByte())

        // vaultBaseBump: 1 byte
        data.add(vaultBaseBump.toByte())

        // padding: 1 byte
        data.add(0)

        return data
    }
}
