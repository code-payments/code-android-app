package com.flipcash.services.models.chat

import com.getcode.libs.parcelable.Parcelable
import com.getcode.libs.parcelable.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val blurhash: String,
): Parcelable
