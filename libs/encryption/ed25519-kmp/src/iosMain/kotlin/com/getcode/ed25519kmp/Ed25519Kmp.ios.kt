package com.getcode.ed25519kmp

import ed25519.ed25519_create_keypair
import ed25519.ed25519_on_curve
import ed25519.ed25519_sign
import ed25519.ed25519_verify
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.UByteVar

/**
 * iOS actual: calls the vendored orlp/ed25519 C library directly via Kotlin/Native cinterop.
 *
 * Memory safety:
 * - All byte arrays are pinned or copied into C-managed memory inside [memScoped].
 * - Output buffers are stack-allocated inside [memScoped] then copied to [ByteArray].
 *
 * Sizes (from ed25519.h):
 *   publicKey  = 32 bytes
 *   privateKey = 64 bytes  (seed || public key, orlp convention)
 *   signature  = 64 bytes
 */
@OptIn(ExperimentalForeignApi::class)
actual object Ed25519Kmp {

    actual fun createKeyPair(seed: ByteArray): KeyPair = memScoped {
        require(seed.size == 32) { "seed must be 32 bytes, got ${seed.size}" }
        val pubKey = allocArray<UByteVar>(32)
        val privKey = allocArray<UByteVar>(64)
        val seedPtr = seed.toUByteArray().toCValues().ptr
        ed25519_create_keypair(pubKey, privKey, seedPtr)
        KeyPair(
            publicKey = pubKey.readBytes(32),
            privateKey = privKey.readBytes(64),
        )
    }

    actual fun sign(
        message: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray = memScoped {
        require(publicKey.size == 32) { "publicKey must be 32 bytes" }
        require(privateKey.size == 64) { "privateKey must be 64 bytes" }
        val sigBuf = allocArray<UByteVar>(64)
        val msgPtr = message.toUByteArray().toCValues().ptr
        val pubPtr = publicKey.toUByteArray().toCValues().ptr
        val privPtr = privateKey.toUByteArray().toCValues().ptr
        ed25519_sign(sigBuf, msgPtr, message.size.convert(), pubPtr, privPtr)
        sigBuf.readBytes(64)
    }

    actual fun verify(
        signature: ByteArray,
        message: ByteArray,
        publicKey: ByteArray,
    ): Boolean = memScoped {
        require(signature.size == 64) { "signature must be 64 bytes" }
        require(publicKey.size == 32) { "publicKey must be 32 bytes" }
        val sigPtr = signature.toUByteArray().toCValues().ptr
        val msgPtr = message.toUByteArray().toCValues().ptr
        val pubPtr = publicKey.toUByteArray().toCValues().ptr
        ed25519_verify(sigPtr, msgPtr, message.size.convert(), pubPtr) != 0
    }

    actual fun onCurve(publicKey: ByteArray): Boolean = memScoped {
        require(publicKey.size == 32) { "publicKey must be 32 bytes" }
        val pubPtr = publicKey.toUByteArray().toCValues().ptr
        ed25519_on_curve(pubPtr) != 0
    }
}
