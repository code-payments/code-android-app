package com.getcode.opencode.tests

import com.getcode.solana.keys.PublicKey
import java.security.KeyPairGenerator
import java.security.SecureRandom

/**
 * Generates a random Public Key for testing purposes.
 */
internal fun generateRandomPublicKeyForTest(): PublicKey {
    // 1. Generate a KeyPair
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048, SecureRandom()) // Use SecureRandom for strong keys
    val keyPair = keyGen.generateKeyPair()

    // 2. Extract the public key bytes
    val publicKeyBytes = keyPair.public.encoded.toList()

    return PublicKey(publicKeyBytes)
}