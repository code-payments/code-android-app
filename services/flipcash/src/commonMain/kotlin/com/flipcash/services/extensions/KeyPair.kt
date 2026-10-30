package com.flipcash.services.extensions

import com.flipcash.services.internal.extensions.toPublicKey
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import java.io.ByteArrayOutputStream

fun derivePoolBetId(poolId: ID, userId: ID): PublicKey {
    val buffer = ByteArrayOutputStream()
    buffer.write(poolId.toByteArray())
    buffer.write(userId.toByteArray())
    val hash = Sha256Hash.hash(buffer.toByteArray())
    // Same key as the former Base64.DEFAULT-encoded seed string.
    return Ed25519.createKeyPair(hash).publicKeyBytes.toPublicKey()
}