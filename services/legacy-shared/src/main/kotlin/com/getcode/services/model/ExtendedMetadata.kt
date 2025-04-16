package com.getcode.services.model

import com.getcode.model.SocialUser

sealed interface ExtendedMetadata {
    data class Tip(val socialUser: SocialUser): ExtendedMetadata
    data class Any(val data: ByteArray, val typeUrl: String): ExtendedMetadata {
        override fun equals(other: kotlin.Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Any

            if (!data.contentEquals(other.data)) return false
            if (typeUrl != other.typeUrl) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + typeUrl.hashCode()
            return result
        }

    }
}