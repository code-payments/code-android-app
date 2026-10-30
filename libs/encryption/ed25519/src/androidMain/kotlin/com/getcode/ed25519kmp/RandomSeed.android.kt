package com.getcode.ed25519kmp

import com.getcode.ed25519.Ed25519 as JniEd25519

internal actual fun randomSeed(size: Int): ByteArray = when (size) {
    16 -> JniEd25519.createSeed16()
    32 -> JniEd25519.createSeed32()
    else -> error("Unsupported seed size $size")
}
