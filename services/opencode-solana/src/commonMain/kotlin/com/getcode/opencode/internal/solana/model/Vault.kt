package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable(with = PublicKeyAsStringSerializer::class)
class Vault(bytes: List<Byte>): PublicKey(bytes) {
    constructor(base58: String) : this(Base58.decode(base58).toList())

    companion object {
        val usdf: Vault
            get() = Vault("FmpZMBbtM2vu7vwmRAAQZa7a6jvQntmmoSYCYWXv4EeX")
        val usdc: Vault
            get() = Vault("3W6Czwv4iWtvv1heeb7MNK97NqW3PmxNvvYW2vipBdsS")
    }
}