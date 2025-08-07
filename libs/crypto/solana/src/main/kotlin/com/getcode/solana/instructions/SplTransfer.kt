package com.getcode.solana.instructions

import com.getcode.solana.instructions.programs.AssociatedTokenProgram
import com.getcode.solana.instructions.programs.SysVar
import com.getcode.solana.instructions.programs.SystemProgram
import com.getcode.solana.instructions.programs.TokenProgram
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
    // Accounts for the Transfer instruction
    val accounts = listOf(
        AccountMeta(sourceTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Source token account
        AccountMeta(destinationTokenAccount.asSolanaPublicKey(), isSigner = false, isWritable = true), // Destination token account
        AccountMeta(sender.asSolanaPublicKey(), isSigner = true, isWritable = false) // Authority (sender's wallet)
    )

    // Instruction data: [Instruction ID (3 for Transfer), Amount (8 bytes)]
    val buffer = ByteBuffer.allocate(9)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(3.toByte()) // Transfer instruction ID
    buffer.putLong(quarks) // Amount
    val instructionData = buffer.array()

    // Create the TransactionInstruction
    return TransactionInstruction(
        programId = TokenProgram.address.asSolanaPublicKey(),
        accounts = accounts,
        data = instructionData
    )
}

internal fun PublicKey.asSolanaPublicKey() = SolanaPublicKey(byteArray)