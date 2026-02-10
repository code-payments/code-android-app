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

        private val jeffy: Mint
            get() = Mint("54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25")

        private val bogey: Mint
            get() = Mint("3AhBb1fpDTp1F9hPkZjRPDejXBM9S5vfpVdvn66vLYnT")

        private val marketCoin: Mint
            get() = Mint("311m6Sb1814PfAxkEcqq6MNdBiVZLr8VWuAWDSC72euW")

        private val xp: Mint
            get() = Mint("6oZnhB1FPrUaDfhRCVZnbVWNKVx9wgj84vKGH7eMpzXL")

        private val float: Mint
            get() = Mint("5APqK9YUZupKt7rRUrpYy6WV3RPuxA71ZtKJffDUMdPP")

        private val bits: Mint
            get() = Mint("A3e8dzb1y4gqGP2cnCS3UU8dm5YNrFpZBpjjdoZdtfnB")

        private val badboys: Mint
            get() = Mint("64dkhPKhdjc2xg3NLyDjC14wiXHLnGXHHUxJnqZVugJt")

        private val sandbox: Mint
            get() = Mint("2psDP3LAvbNzfvBYNMs9ieMpsD8PVzyQsKNfZrjEKoDN")

    }
}