package com.getcode.solana.keys

import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable(with = PublicKeyAsStringSerializer::class)
class Mint(bytes: List<Byte>): PublicKey(bytes) {
    constructor(base58: String) : this(Base58.decode(base58).toList())

    companion object {
        val kin: Mint
            get() = Mint("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6")

        val usdc: Mint
            get() = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

        val usdf: Mint
            get() = Mint("5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ")

        val jeffy: Mint
            get() = Mint("54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25")
    }
}