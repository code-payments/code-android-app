package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the server-mediated stablecoin swap instruction for [swapVersion]: SwapV2 for exactly 2
 * (matching the server's rule), otherwise the legacy Swap with [whitelist].
 */
internal fun coinbaseStableSwapperSwapInstruction(
    swapVersion: Int,
    pool: PublicKey,
    inVault: PublicKey,
    outVault: PublicKey,
    inVaultTokenAccount: PublicKey,
    outVaultTokenAccount: PublicKey,
    userFromTokenAccount: PublicKey,
    toTokenAccount: PublicKey,
    feeRecipientTokenAccount: PublicKey,
    feeRecipient: PublicKey,
    fromMint: PublicKey,
    toMint: PublicKey,
    user: PublicKey,
    whitelist: PublicKey,
    amountIn: Long,
    minAmountOut: Long,
): Instruction = if (swapVersion == 2) {
    CoinbaseStableSwapperProgram_SwapV2(
        pool = pool, inVault = inVault, outVault = outVault,
        inVaultTokenAccount = inVaultTokenAccount, outVaultTokenAccount = outVaultTokenAccount,
        userFromTokenAccount = userFromTokenAccount, toTokenAccount = toTokenAccount,
        feeRecipientTokenAccount = feeRecipientTokenAccount, feeRecipient = feeRecipient,
        fromMint = fromMint, toMint = toMint, user = user,
        amountIn = amountIn, minAmountOut = minAmountOut,
    ).instruction()
} else {
    CoinbaseStableSwapperProgram_Swap(
        pool = pool, inVault = inVault, outVault = outVault,
        inVaultTokenAccount = inVaultTokenAccount, outVaultTokenAccount = outVaultTokenAccount,
        userFromTokenAccount = userFromTokenAccount, toTokenAccount = toTokenAccount,
        feeRecipientTokenAccount = feeRecipientTokenAccount, feeRecipient = feeRecipient,
        fromMint = fromMint, toMint = toMint, user = user, whitelist = whitelist,
        amountIn = amountIn, minAmountOut = minAmountOut,
    ).instruction()
}
