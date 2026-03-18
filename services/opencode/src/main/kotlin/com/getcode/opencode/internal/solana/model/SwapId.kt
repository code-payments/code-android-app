package com.getcode.opencode.internal.solana.model

import android.os.Parcelable
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.ByteListAsBase64Serializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
@Parcelize
value class SwapId(@Serializable(with = ByteListAsBase64Serializer::class) val value: List<Byte>): Parcelable {

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