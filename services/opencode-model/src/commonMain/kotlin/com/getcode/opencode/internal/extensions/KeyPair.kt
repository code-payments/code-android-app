package com.getcode.opencode.internal.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi public fun KeyPair.toPublicKey(): PublicKey {
    return PublicKey(this.publicKeyBytes.toList())
}