package com.getcode.opencode.internal.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi public fun PublicKey.Companion.fromUbytes(bytes: List<UByte>): PublicKey {
    return PublicKey(bytes.map { it.toByte() })
}