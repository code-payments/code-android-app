package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class MediaItemRendition(
    val role: Role,
    val blobId: BlobId,
    val blob: BlobMetadata?,
): Parcelable {
    enum class Role {
        UNKNOWN,
        ORIGINAL,
        DISPLAY,
        THUMBNAIL,
    }
}
