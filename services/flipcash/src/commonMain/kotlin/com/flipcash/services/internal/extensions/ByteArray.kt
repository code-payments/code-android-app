package com.flipcash.services.internal.extensions

import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

internal fun ByteArray.toHash(): com.getcode.solana.keys.Hash {
    return com.getcode.solana.keys.Hash(this.toList())
}

internal fun ByteArray.toChecksum(): Checksum {
    return Checksum(this.toList())
}

internal fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

internal fun ByteArray.toMint(): Mint {
    return Mint(this.toList())
}

internal fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
}