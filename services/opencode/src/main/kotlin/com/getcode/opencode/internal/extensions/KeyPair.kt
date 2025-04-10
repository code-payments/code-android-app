package com.getcode.opencode.internal.extensions

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.PublicKey

internal fun KeyPair.toPublicKey(): PublicKey {
    return PublicKey(this.publicKeyBytes.toList())
}