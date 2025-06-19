package com.getcode.opencode.utils

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.solana.keys.PublicKey

fun PublicKey.Companion.generate(): PublicKey = Ed25519.createSeed32().toPublicKey()