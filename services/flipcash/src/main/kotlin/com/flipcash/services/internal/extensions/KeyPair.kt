package com.flipcash.services.internal.extensions

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.PublicKey

internal fun KeyPair.asPublicKey(): PublicKey {
    return PublicKey(this.publicKeyBytes.toList())
}