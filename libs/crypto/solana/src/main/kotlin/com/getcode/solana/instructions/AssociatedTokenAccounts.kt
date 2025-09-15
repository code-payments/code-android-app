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
    mint: PublicKey, // Token mint (e.g., USDC)
    ata: PublicKey, // Associated Token Account (ATA)
    payer: PublicKey, // Wallet paying for the account creation (sender)
    owner: PublicKey, // Wallet to create ATA for (sender or recipient)
    programId: PublicKey = TokenProgram.address
): TransactionInstruction {
    return TransactionInstruction(
        programId = AssociatedTokenProgram.address.asSolanaPublicKey(),
        accounts = listOf(
            AccountMeta(payer.asSolanaPublicKey(), isSigner = true, isWritable = true), // Payer (sender)
            AccountMeta(ata.asSolanaPublicKey(), isSigner = false, isWritable = true), // ATA to create
            AccountMeta(owner.asSolanaPublicKey(), isSigner = false, isWritable = false), // Wallet address
            AccountMeta(mint.asSolanaPublicKey(), isSigner = false, isWritable = false), // Token mint
            AccountMeta(SystemProgram.address.asSolanaPublicKey(), isSigner = false, isWritable = false), // System Program
            AccountMeta(programId.asSolanaPublicKey(), isSigner = false, isWritable = false), // Token Program
            AccountMeta(SysVar.rent.address().asSolanaPublicKey(), isSigner = false, isWritable = false) // Rent Sysvar
        ),
        data = byteArrayOf()
    )
}