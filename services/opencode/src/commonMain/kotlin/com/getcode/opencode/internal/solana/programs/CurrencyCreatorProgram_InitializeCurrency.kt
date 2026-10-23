package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey

@Suppress("ClassName")
internal class CurrencyCreatorProgram_InitializeCurrency(
    private val authority: PublicKey,
    private val mint: PublicKey,
    private val currency: PublicKey,
    private val name: String,
    private val symbol: String,
    private val seed: PublicKey,
    private val bump: Int,
    private val mintBump: Int,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = CurrencyCreatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = authority, signer = true),
                AccountMeta.writable(publicKey = mint),
                AccountMeta.writable(publicKey = currency),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(CurrencyCreatorProgram.Command.initializeCurrency.value)

        // name: 32 bytes, right-padded with zeros
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val namePadded = ByteArray(32)
        nameBytes.copyInto(namePadded, 0, 0, minOf(nameBytes.size, 32))
        data.addAll(namePadded.toList())

        // symbol: 8 bytes, right-padded with zeros
        val symbolBytes = symbol.toByteArray(Charsets.UTF_8)
        val symbolPadded = ByteArray(8)
        symbolBytes.copyInto(symbolPadded, 0, 0, minOf(symbolBytes.size, 8))
        data.addAll(symbolPadded.toList())

        // seed: 32 bytes (pubkey)
        data.addAll(seed.bytes)

        // bump: 1 byte
        data.add(bump.toByte())

        // mintBump: 1 byte
        data.add(mintBump.toByte())

        // padding: 6 bytes of zeros
        data.addAll(ByteArray(6).toList())

        return data
    }
}
