package com.flipcash.services.internal.extensions

import com.getcode.solana.keys.PublicKey

internal fun ByteArray.toHash(): com.getcode.solana.keys.Hash {
    return com.getcode.solana.keys.Hash(this.toList())
}

internal fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

internal fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
}