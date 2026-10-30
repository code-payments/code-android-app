package com.getcode.opencode.internal.extensions

import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

internal fun ByteArray.toHash(): com.getcode.solana.keys.Hash {
    return com.getcode.solana.keys.Hash(this.toList())
}

internal fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

internal fun ByteArray.toSignature(): Signature {
    return Signature(this.toList())
}

internal fun ByteArray.toMint(): Mint {
    return Mint(this.toList())
}

internal fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
}