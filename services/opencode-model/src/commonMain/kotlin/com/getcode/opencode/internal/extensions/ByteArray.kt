package com.getcode.opencode.internal.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

@InternalOpenCodeApi public fun ByteArray.toHash(): com.getcode.solana.keys.Hash {
    return com.getcode.solana.keys.Hash(this.toList())
}

@InternalOpenCodeApi public fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

@InternalOpenCodeApi public fun ByteArray.toSignature(): Signature {
    return Signature(this.toList())
}

@InternalOpenCodeApi public fun ByteArray.toMint(): Mint {
    return Mint(this.toList())
}

@InternalOpenCodeApi public fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
}