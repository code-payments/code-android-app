package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Identifies one immutable set of blob bytes.
 *
 * Not a value class, because a value class may not override [equals], and the default over a
 * `ByteArray` compares array identity. Every read of a persisted [MediaItem] decodes fresh arrays,
 * so identity equality makes each read a distinct [MediaItem] — and Compose keys off that, so
 * `remember(media)` drops its state and cancels the effects holding it on every emission, which is
 * enough to stop a re-minted download URL from ever reaching the image loader.
 *
 * [BlobIdSerializer] keeps the wrapper invisible on the wire, which a value class got for free.
 */
@Parcelize
@Serializable(with = BlobIdSerializer::class)
data class BlobId(val bytes: ByteArray) : Parcelable {

    override fun equals(other: Any?): Boolean =
        this === other || (other is BlobId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * Encodes a [BlobId] as its bare bytes.
 *
 * `MediaItem`s are persisted as JSON (`user_profiles.profile_picture_json`, chat message contents),
 * and every row written while [BlobId] was a value class holds the id inlined — `"blobId":[1,2,…]`.
 * The class-shaped default would write `"blobId":{"bytes":[1,2,…]}` and fail to decode those rows,
 * which drops the whole `MediaItem` and leaves the surface with no image at all.
 */
object BlobIdSerializer : KSerializer<BlobId> {
    private val delegate = ByteArraySerializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("com.flipcash.services.models.chat.BlobId", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: BlobId) =
        encoder.encodeSerializableValue(delegate, value.bytes)

    override fun deserialize(decoder: Decoder): BlobId =
        BlobId(decoder.decodeSerializableValue(delegate))
}
