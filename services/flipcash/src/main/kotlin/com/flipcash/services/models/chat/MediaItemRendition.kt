package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class MediaItemRendition(
    val role: Role,
    val blobId: BlobId,
    val blob: BlobMetadata?,
) {
    enum class Role {
        UNKNOWN,
        ORIGINAL,
        DISPLAY,
        THUMBNAIL,
    }
}
