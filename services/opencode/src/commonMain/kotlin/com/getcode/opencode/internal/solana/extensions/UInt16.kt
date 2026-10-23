package com.getcode.opencode.internal.solana.extensions

internal fun Int.toU16Bytes(): List<Byte> = listOf(
    (this and 0xFF).toByte(),
    (this shr 8 and 0xFF).toByte()
)