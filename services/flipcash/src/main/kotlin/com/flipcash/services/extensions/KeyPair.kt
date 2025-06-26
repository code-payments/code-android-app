package com.flipcash.services.extensions

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Seed32

fun derivePoolBetId(poolId: ID, userId: ID): KeyPair {
    val hash = Sha256Hash.hash((poolId + userId).toByteArray())
    val seed = Seed32(hash.toList())
    return Ed25519.createKeyPair(seed.byteArray)
}