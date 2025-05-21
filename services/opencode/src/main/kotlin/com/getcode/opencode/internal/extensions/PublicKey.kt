package com.getcode.opencode.internal.extensions

import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey

internal fun PublicKey.Companion.generate(): PublicKey = Ed25519.createSeed32().toPublicKey()

internal fun PublicKey.Companion.fromUbytes(bytes: List<UByte>): PublicKey {
    return PublicKey(bytes.map { it.toByte() })
}