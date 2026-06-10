package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.TokenProgram_Transfer
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * Builds instructions for a simple USDC SPL token transfer from an external
 * wallet ([sender]) to the app wallet [owner]'s USDC ATA.
 *
 * The server's USDC deposit sweep will detect the incoming balance and
 * convert it to USDF automatically.
 */
internal fun buildUsdcDepositInstructions(
    sender: PublicKey,
    owner: PublicKey,
    amount: Long,
): List<Instruction> {
    val senderUsdcAta = PublicKey.deriveAssociatedAccount(
        owner = sender,
        mint = Mint.usdc,
    ).publicKey

    val ownerUsdcAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = sender,
        owner = owner,
        mint = Mint.usdc,
    )

    return buildList {
        // 1. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = 100_000).instruction())
        // 2. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 1_000).instruction())
        // 3. AssociatedTokenAccount::CreateIdempotent (owner's USDC ATA)
        add(ownerUsdcAta.instruction())
        // 4. TokenProgram::Transfer (sender USDC ATA → owner USDC ATA)
        add(
            TokenProgram_Transfer(
                amount = amount,
                source = senderUsdcAta,
                destination = ownerUsdcAta.address,
                owner = sender,
            ).instruction()
        )
    }
}
