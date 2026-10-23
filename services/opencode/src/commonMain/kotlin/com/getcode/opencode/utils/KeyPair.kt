package com.getcode.opencode.utils

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519kmp.Ed25519
import com.getcode.ed25519kmp.Ed25519.KeyPair

fun deriveRendezvousKey(from: ByteArray): KeyPair {
    // Same key and seed string as the former Base64.DEFAULT-encoded seed.
    return Ed25519.createKeyPair(Sha256Hash.hash(from))
}
