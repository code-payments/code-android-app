package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val renditions: List<MediaItemRendition>,
)
