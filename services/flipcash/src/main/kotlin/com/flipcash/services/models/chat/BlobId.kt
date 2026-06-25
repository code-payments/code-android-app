package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class BlobId(val bytes: ByteArray)
