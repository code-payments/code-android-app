package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MediaId(val bytes: ByteArray)
