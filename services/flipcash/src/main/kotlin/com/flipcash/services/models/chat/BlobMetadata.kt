package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class BlobMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val image: ImageMetadata?,
): Parcelable
