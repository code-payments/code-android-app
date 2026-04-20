package com.flipcash.services.internal.model.moderation

import com.codeinc.flipcash.gen.moderation.v1.ModerationService

interface ModerationRequest {
    data class Text(val text: String, val response: ModerationService.ModerateTextResponse): ModerationRequest
    data class Image(val imageBytes: ByteArray, val response: ModerationService.ModerateImageResponse): ModerationRequest {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (response != other.response) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageBytes.contentHashCode()
            result = 31 * result + response.hashCode()
            return result
        }
    }
}