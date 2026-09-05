package com.getcode.crypt

import com.getcode.utils.subByteArray

/// Deterministic wallet generation for ED25519 curve using SLIP-0010 spec
/// Reference: https://github.com/satoshilabs/slips/blob/master/slip-0010.md
object Derive {
    private const val curve = "ed25519 seed"
    private const val algorithm = "HmacSHA512"
    private const val hardenedOffset = 0x80000000L

    /** Derives the raw 32-byte private key for [path] (defaults to [DerivePath.primary]) from [seed]. */
    fun derivedKey(seed: ByteArray, path: DerivePath? = null): ByteArray {
        val indexes = (path?.indexes ?: DerivePath.primary.indexes).map { hardenedOffset + it.value }
        return derivedKey(seed, indexes.toLongArray())
    }

    /** Derives the raw 32-byte private key by walking [hardenedIndexes] (each already offset by 0x80000000) from [seed]. */
    fun derivedKey(seed: ByteArray, hardenedIndexes: LongArray): ByteArray {
        var descriptor = masterKey(seed)
        hardenedIndexes.forEach { index ->
            descriptor = CKDPriv(keyDescriptor = descriptor, index = index)
        }
        return descriptor.key
    }

    private fun CKDPriv(keyDescriptor: KeyDescriptor, index: Long): KeyDescriptor {
        val i = index.toInt()
        val entropy = mutableListOf<Byte>()
        entropy.add(0)
        entropy.addAll(keyDescriptor.key.toList())
        entropy.add((i ushr 24).toByte())
        entropy.add((i ushr 16).toByte())
        entropy.add((i ushr 8).toByte())
        entropy.add(i.toByte())

        return split32(
            hmac(key = keyDescriptor.chain, message = entropy.toByteArray())
        )
    }

    private fun masterKey(seed: ByteArray): KeyDescriptor {
        val descriptor = hmac(curve.encodeToByteArray(), seed)
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
            if (other !is KeyDescriptor) return false

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
