package com.flipcash.services.models.chat

import com.getcode.libs.parcelable.ManualParcelable
import com.getcode.libs.parcelable.Parcel
import com.getcode.libs.parcelable.parcelableCreator
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
data class ChatId(val bytes: ByteArray) : ManualParcelable {

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
        val CREATOR: Any = parcelableCreator({ arrayOfNulls<ChatId>(it) }) { parcel -> ChatId(parcel) }
    }
}
