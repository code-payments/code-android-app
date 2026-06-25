package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
data class BlobMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val image: ImageMetadata?,
)
