package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class MediaMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val blurhash: String,
    val durationMs: Long,
)
