package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val blurhash: String,
)
