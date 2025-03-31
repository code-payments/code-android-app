package com.getcode.model.extensions

import com.getcode.solana.keys.PreSwapStateAccount
import com.getcode.solana.keys.PublicKey

fun PreSwapStateAccount.Companion.newInstance(
    owner: PublicKey,
    source: PublicKey,
    destination: PublicKey,
    nonce: PublicKey
): PreSwapStateAccount {
    return PreSwapStateAccount(
        owner = owner,
        state = PublicKey.derivePreSwapState(source, destination, nonce)
    )
}