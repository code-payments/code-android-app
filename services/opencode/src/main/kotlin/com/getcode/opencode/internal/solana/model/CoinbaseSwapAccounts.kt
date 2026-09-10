package com.getcode.opencode.internal.solana.model

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.deriveCoinbasePoolAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseTokenVaultAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseVaultTokenAccountAddress
import com.getcode.opencode.internal.solana.extensions.deriveCoinbaseWhitelistAddress
import com.getcode.solana.keys.PublicKey

internal data class CoinbaseSwapAccounts(
    val pool: PublicKey,
    val inVault: PublicKey,
    val outVault: PublicKey,
    val inVaultTokenAccount: PublicKey,
    val outVaultTokenAccount: PublicKey,
    val whitelist: PublicKey,
) {
    fun feeRecipientTokenAccount(feeRecipient: PublicKey, fromMint: PublicKey): PublicKey {
        return PublicKey.deriveAssociatedAccount(
            owner = feeRecipient,
            mint = fromMint,
        ).publicKey
    }

    companion object {
        fun derive(fromMint: PublicKey, toMint: PublicKey): CoinbaseSwapAccounts {
            val pool = PublicKey.deriveCoinbasePoolAddress().publicKey
            val inVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, fromMint).publicKey
            val outVault = PublicKey.deriveCoinbaseTokenVaultAddress(pool, toMint).publicKey
            val inVaultTokenAccount = PublicKey.deriveCoinbaseVaultTokenAccountAddress(inVault).publicKey
            val outVaultTokenAccount = PublicKey.deriveCoinbaseVaultTokenAccountAddress(outVault).publicKey
            val whitelist = PublicKey.deriveCoinbaseWhitelistAddress().publicKey

            return CoinbaseSwapAccounts(
                pool = pool,
                inVault = inVault,
                outVault = outVault,
                inVaultTokenAccount = inVaultTokenAccount,
                outVaultTokenAccount = outVaultTokenAccount,
                whitelist = whitelist,
            )
        }
    }
}
