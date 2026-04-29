package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes

internal class CoinbaseStableSwapperProgram_Swap(
    private val pool: PublicKey,
    private val inVault: PublicKey,
    private val outVault: PublicKey,
    private val inVaultTokenAccount: PublicKey,
    private val outVaultTokenAccount: PublicKey,
    private val userFromTokenAccount: PublicKey,
    private val toTokenAccount: PublicKey,
    private val feeRecipientTokenAccount: PublicKey,
    private val feeRecipient: PublicKey,
    private val fromMint: PublicKey,
    private val toMint: PublicKey,
    private val user: PublicKey,
    private val whitelist: PublicKey,
    private val amountIn: Long,
    private val minAmountOut: Long,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = CoinbaseStableSwapperProgram.address,
            accounts = listOf(
                AccountMeta.readonly(publicKey = pool),
                AccountMeta.readonly(publicKey = inVault),
                AccountMeta.readonly(publicKey = outVault),
                AccountMeta.writable(publicKey = inVaultTokenAccount),
                AccountMeta.writable(publicKey = outVaultTokenAccount),
                AccountMeta.writable(publicKey = userFromTokenAccount),
                AccountMeta.writable(publicKey = toTokenAccount),
                AccountMeta.writable(publicKey = feeRecipientTokenAccount),
                AccountMeta.readonly(publicKey = feeRecipient),
                AccountMeta.readonly(publicKey = fromMint),
                AccountMeta.readonly(publicKey = toMint),
                AccountMeta.writable(publicKey = user, signer = true),
                AccountMeta.readonly(publicKey = whitelist),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = AssociatedTokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
            ),
            data = encode(),
        )
    }

    override fun encode(): List<Byte> = buildList {
        // 8-byte Anchor discriminator: [248, 198, 158, 145, 225, 117, 135, 200]
        addAll(DISCRIMINATOR.toList())
        addAll(amountIn.bytes)
        addAll(minAmountOut.bytes)
    }

    companion object {
        private val DISCRIMINATOR = byteArrayOf(
            248.toByte(), 198.toByte(), 158.toByte(), 145.toByte(),
            225.toByte(), 117.toByte(), 135.toByte(), 200.toByte(),
        )
    }
}
