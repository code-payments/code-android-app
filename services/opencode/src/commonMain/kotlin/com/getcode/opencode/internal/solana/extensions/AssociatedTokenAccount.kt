package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

internal fun AssociatedTokenAccount.Companion.newInstance(owner: PublicKey, mint: Mint): AssociatedTokenAccount {
    return AssociatedTokenAccount(
        owner = owner,
        ata = PublicKey.deriveAssociatedAccount(owner, mint)
    )
}