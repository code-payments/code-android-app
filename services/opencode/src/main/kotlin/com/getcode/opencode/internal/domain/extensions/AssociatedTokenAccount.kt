package com.getcode.opencode.internal.domain.extensions

import com.getcode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

fun AssociatedTokenAccount.Companion.newInstance(
    owner: PublicKey,
    mint: Mint
): AssociatedTokenAccount {
    return AssociatedTokenAccount(
        owner = owner,
        ata = PublicKey.deriveAssociatedAccount(owner = owner, mint = mint)
    )
}