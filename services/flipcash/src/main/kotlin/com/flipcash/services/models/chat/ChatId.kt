package com.flipcash.services.models.chat

import android.os.Parcel
import android.os.Parcelable
import com.getcode.utils.base58
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ChatIdAsHexSerializer : KSerializer<ChatId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ChatId", PrimitiveKind.STRING)

    @OptIn(ExperimentalStdlibApi::class)
    override fun serialize(encoder: Encoder, value: ChatId) {
        encoder.encodeString(value.bytes.toHexString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(decoder: Decoder): ChatId {
        return ChatId(decoder.decodeString().hexToByteArray())
    }
}

@Serializable(with = ChatIdAsHexSerializer::class)
data class ChatId(val bytes: ByteArray) : Parcelable {

    constructor(bytes: List<Byte>): this(bytes.toByteArray())
    @OptIn(ExperimentalStdlibApi::class)
    constructor(hex: String): this(hex.hexToByteArray())

    private constructor(parcel: Parcel): this(parcel.readString().orEmpty())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatId) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = bytes.base58

    override fun describeContents(): Int = 0

    @OptIn(ExperimentalStdlibApi::class)
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(bytes.toHexString())
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ChatId> =
            object : Parcelable.Creator<ChatId> {
                override fun createFromParcel(parcel: Parcel) = ChatId(parcel)
                override fun newArray(size: Int) = arrayOfNulls<ChatId?>(size)
            }
    }
}
