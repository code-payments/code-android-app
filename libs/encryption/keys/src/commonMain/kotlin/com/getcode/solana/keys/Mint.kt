package com.getcode.solana.keys

import com.getcode.utils.serializer.MintAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable(with = MintAsStringSerializer::class)
class Mint(bytes: List<Byte>): PublicKey(bytes) {
    constructor(base58: String) : this(Base58.decode(base58).toList())

    companion object {
        val usdc: Mint
            get() = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

        val usdf: Mint
            get() = Mint("5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ")

    }
}
