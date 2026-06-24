package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val mediaId: MediaId,
    val metadata: MediaMetadata?,
)
