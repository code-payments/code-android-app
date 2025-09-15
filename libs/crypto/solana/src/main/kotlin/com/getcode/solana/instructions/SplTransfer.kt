package com.getcode.solana.instructions

import com.getcode.solana.instructions.programs.TokenProgram
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder


fun createSplTransfer(
    sender: PublicKey,
    sourceTokenAccount: PublicKey,
    destinationTokenAccount: PublicKey,
    quarks: Long,
): TransactionInstruction {
    // Create the TransactionInstruction
    return TransactionInstruction(
        programId = TokenProgram.address.asSolanaPublicKey(),
        accounts = listOf(
            AccountMeta(sourceTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Source token account
            AccountMeta(destinationTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Destination token account
            AccountMeta(sender.asSolanaPublicKey(), isSigner = true, isWritable = true) // Authority (sender's wallet)
        ),
        data = ByteBuffer.allocate(9).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(3.toByte())
            putLong(quarks)
        }.array()
    )
}

fun createSplCheckedTransfer(
    sender: PublicKey,
    mint: Mint,
    sourceTokenAccount: PublicKey,
    destinationTokenAccount: PublicKey,
    quarks: Long,
    decimals: Int,
): TransactionInstruction {
    // Create the TransactionInstruction
    return TransactionInstruction(
        programId = TokenProgram.address.asSolanaPublicKey(),
        accounts = listOf(
            AccountMeta(sourceTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Source token account
            AccountMeta(mint.asSolanaPublicKey(), isSigner = false, isWritable = false), // Mint to check against
            AccountMeta(destinationTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Destination token account
            AccountMeta(sender.asSolanaPublicKey(), isSigner = true, isWritable = true) // Authority (sender's wallet)
        ),
        data = ByteBuffer.allocate(10).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(12.toByte())
            putLong(quarks)
            put(decimals.toByte())
        }.array()
    )
}

internal fun PublicKey.asSolanaPublicKey() = SolanaPublicKey(byteArray)