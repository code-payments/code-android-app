package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val blurhash: String,
): Parcelable
