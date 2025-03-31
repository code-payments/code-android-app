package com.getcode.crypt

import com.getcode.ed25519.Ed25519
import com.getcode.utils.encodeBase64
import org.kin.sdk.base.tools.subByteArray
import java.nio.ByteBuffer


object Derive {
    private const val curve = "ed25519 seed"
    private const val algorithm = "HmacSHA512"
    private const val hardenedOffset = 0x80000000

    fun path(seed: ByteArray, path: DerivePath? = null): Ed25519.KeyPair {
        var descriptor = masterKey(seed)
        (path?.indexes?.map { it } ?: DerivePath.primary.indexes).forEach { index ->
            descriptor = CKDPriv(
                keyDescriptor = descriptor,
                index = hardenedOffset + index.value
            )
        }

        return Ed25519.createKeyPair(descriptor.key.encodeBase64())
    }

    private fun CKDPriv(keyDescriptor: KeyDescriptor, index: Long): KeyDescriptor {
        val entropy = mutableListOf<Byte>()
        entropy.add(0)
        entropy.addAll(keyDescriptor.key.toList())

        ByteBuffer.allocate(Int.SIZE_BYTES).apply {
            putInt(index.toInt())
            entropy.addAll(array().toList())
        }

        return split32(
            hmac(key = keyDescriptor.chain, message = entropy.toByteArray())
        )
    }

    private fun masterKey(seed: ByteArray): KeyDescriptor {
        val descriptor = hmac(curve.toByteArray(), seed)
        return split32(descriptor)
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        return Hmac.hmac(algorithm, key, message)
    }

    private fun split32(array: ByteArray): KeyDescriptor {
        return KeyDescriptor(
            key = array.subByteArray(0, 32),
            chain = array.subByteArray(32, 32)
        )
    }

    data class KeyDescriptor(val key: ByteArray, val chain: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyDescriptor

            if (!key.contentEquals(other.key)) return false
            if (!chain.contentEquals(other.chain)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + chain.contentHashCode()
            return result
        }
    }
}