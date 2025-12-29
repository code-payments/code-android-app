package com.getcode.opencode.internal.solana.model

import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

@JvmInline
value class SwapId(val value: List<Byte>) {

    init {
        require(value.size == 32) { "SwapId must be exactly 32 bytes" }
    }

    constructor(publicKey: PublicKey) : this(publicKey.bytes)

    val publicKey: PublicKey
        get() = PublicKey(value)

    companion object {
        fun fromBytes(data: List<Byte>): SwapId? {
            return if (data.size == 32) SwapId(data) else null
        }

        fun generate(): SwapId = SwapId(PublicKey.generate())
    }
}