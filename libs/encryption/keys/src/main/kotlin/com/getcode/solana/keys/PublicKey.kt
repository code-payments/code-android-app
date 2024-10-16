package com.getcode.solana.keys

import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.vendor.Base58
import com.google.protobuf.ByteString
import kotlinx.serialization.Serializable

@Serializable(with = PublicKeyAsStringSerializer::class)
class PublicKey(bytes: List<Byte>) : Key32(bytes) {

    constructor(base58: String): this(Base58.decode(base58).toList())

    companion object {

        val kin: Mint
            get() = Mint(Base58.decode("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6").toList())

        val usdc: Mint
            get() = Mint(org.kin.sdk.base.tools.Base58.decode("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v").toList())

        fun fromBase58(base58: String): PublicKey {
            return PublicKey(Base58.decode(base58).toList())
        }

        fun fromByteString(byteString: ByteString): PublicKey {
            return PublicKey(byteString.toByteArray().toList())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as PublicKey
        return size == other.size && bytes == other.bytes
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + size
        return result
    }

}