package com.getcode.opencode.solana

import com.getcode.solana.keys.PublicKey

internal fun key(seed: Int): PublicKey = PublicKey(ByteArray(32) { seed.toByte() }.toList())

internal fun String.hexToBytes(): List<Byte> =
    if (isEmpty()) emptyList() else chunked(2).map { it.toInt(16).toByte() }

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
