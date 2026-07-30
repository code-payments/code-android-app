package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
@JvmInline
value class BlobId(val bytes: ByteArray): Parcelable
