package com.flipcash.shared.chat.media

import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition

/**
 * The item that sends [this] photo. A sealed photo carries the full metadata its contract needs; a
 * plain one only its id, the server filling in the rest from the bytes.
 */
internal fun UploadedPhoto.toMediaItem(): MediaItem {
    val blob = if (sealedFor != null) {
        BlobMetadata(
            mimeType = JPEG,
            sizeBytes = sizeBytes,
            downloadUrl = "",
            image = ImageMetadata(width, height, blurhash.orEmpty()),
        )
    } else null
    return MediaItem(listOf(MediaItemRendition(MediaItemRendition.Role.ORIGINAL, blobId, blob)))
}

private const val JPEG = "image/jpeg"
