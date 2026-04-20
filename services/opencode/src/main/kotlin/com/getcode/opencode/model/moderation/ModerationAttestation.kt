package com.getcode.opencode.model.moderation

import com.getcode.utils.base58

sealed interface ModerationAttestation {
    val attestation: List<Byte>
    data class Text(val text: String, override val attestation: List<Byte>): ModerationAttestation {
        override fun toString(): String {
            return "Text($text, attestation=${attestation.base58})"
        }
    }
    data class Image(val imageBytes: ByteArray, override val attestation: List<Byte>): ModerationAttestation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (attestation != other.attestation) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageBytes.contentHashCode()
            result = 31 * result + attestation.hashCode()
            return result
        }

        override fun toString(): String {
            return "Image(size=${imageBytes.size}, attestation=${attestation.base58})"
        }
    }
}