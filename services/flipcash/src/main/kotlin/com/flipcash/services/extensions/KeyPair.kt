package com.flipcash.services.extensions

import android.util.Base64
import com.flipcash.services.internal.extensions.toPublicKey
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Seed32
import java.io.ByteArrayOutputStream

fun derivePoolBetId(poolId: ID, userId: ID): PublicKey {
    val buffer = ByteArrayOutputStream()
    buffer.write(poolId.toByteArray())
    buffer.write(userId.toByteArray())
    val hash = Sha256Hash.hash(buffer.toByteArray())
    val seed = Base64.encodeToString(hash, Base64.DEFAULT)
    return Ed25519.createKeyPair(seed).publicKeyBytes.toPublicKey()
}