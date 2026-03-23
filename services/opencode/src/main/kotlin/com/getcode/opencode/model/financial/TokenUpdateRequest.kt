package com.getcode.opencode.model.financial

import com.getcode.opencode.model.ui.TokenBillCustomizations

sealed interface TokenUpdateRequest {
    val token: Token

    data class Metadata(
        override val token: Token,
        val description: String? = null,
        val billCustomization: TokenBillCustomizations? = null,
        val socialLinks: List<SocialLink>? = null,
    ): TokenUpdateRequest

    data class Icon(
        override val token: Token,
        val icon: ByteArray,
    ): TokenUpdateRequest {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Icon

            if (token != other.token) return false
            if (!icon.contentEquals(other.icon)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = token.hashCode()
            result = 31 * result + icon.contentHashCode()
            return result
        }
    }
}
