package com.getcode.solana.instructions

import com.getcode.solana.instructions.programs.AssociatedTokenProgram
import com.getcode.solana.instructions.programs.SysVar
import com.getcode.solana.instructions.programs.SystemProgram
import com.getcode.solana.instructions.programs.TokenProgram
import com.getcode.solana.keys.PublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction


// Function to create an Associated Token Account instruction
fun createAssociatedTokenAccountInstruction(
    payer: PublicKey, // Wallet paying for the account creation (sender)
    walletAddress: PublicKey, // Wallet to create ATA for (sender or recipient)
    ata: PublicKey, // Associated Token Account (ATA)
    mint: PublicKey // Token mint (e.g., USDC)
): TransactionInstruction {
    val accounts = listOf(
        AccountMeta(payer.asSolanaPublicKey(), isSigner = true, isWritable = true), // Payer (sender)
        AccountMeta(ata.asSolanaPublicKey(), isSigner = false, isWritable = true), // ATA to create
        AccountMeta(walletAddress.asSolanaPublicKey(), isSigner = false, isWritable = false), // Wallet address
        AccountMeta(mint.asSolanaPublicKey(), isSigner = false, isWritable = false), // Token mint
        AccountMeta(SystemProgram.address.asSolanaPublicKey(), isSigner = false, isWritable = false), // System Program
        AccountMeta(TokenProgram.address.asSolanaPublicKey(), isSigner = false, isWritable = false), // Token Program
        AccountMeta(SysVar.rent.address().asSolanaPublicKey(), isSigner = false, isWritable = false) // Rent Sysvar
    )

    // Instruction data: Empty for createAssociatedTokenAccount (ID = 1)
    val instructionData = byteArrayOf(1)

    return TransactionInstruction(
        programId = AssociatedTokenProgram.address.asSolanaPublicKey(),
        accounts = accounts,
        data = instructionData
    )
}