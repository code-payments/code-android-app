package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.annotations.InternalOpenCodeApi

@InternalOpenCodeApi public fun Int.toU16Bytes(): List<Byte> = listOf(
    (this and 0xFF).toByte(),
    (this shr 8 and 0xFF).toByte()
)