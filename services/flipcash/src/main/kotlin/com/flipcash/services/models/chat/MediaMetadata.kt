package com.flipcash.services.models.chat

data class MediaMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val blurhash: String,
    val durationMs: Long,
)
