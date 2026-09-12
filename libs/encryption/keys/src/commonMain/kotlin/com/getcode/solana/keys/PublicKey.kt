package com.getcode.solana.keys

import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable(with = PublicKeyAsStringSerializer::class)
open class PublicKey(bytes: List<Byte>) : Key32(bytes) {

    constructor(base58: String): this(Base58.decode(base58).toList())

    val description: String = base58()

    companion object {

        const val MAX_SEEDS = 16

        fun fromBase58(base58: String): PublicKey {
            return PublicKey(base58)
        }

        val ZERO: PublicKey = PublicKey(zero.bytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return size == other.size && bytes == other.bytes
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + size
        return result
    }

    override fun toString(): String {
        return base58()
    }

}
