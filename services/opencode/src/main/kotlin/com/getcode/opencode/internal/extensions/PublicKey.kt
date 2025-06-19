package com.getcode.opencode.internal.extensions

import com.getcode.solana.keys.PublicKey

internal fun PublicKey.Companion.fromUbytes(bytes: List<UByte>): PublicKey {
    return PublicKey(bytes.map { it.toByte() })
}