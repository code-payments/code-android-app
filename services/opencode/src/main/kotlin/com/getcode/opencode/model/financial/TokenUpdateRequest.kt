package com.getcode.opencode.model.financial

import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations

sealed interface TokenUpdateRequest {
    val token: Token

    data class Metadata(
        override val token: Token,
        val description: ModerationAttestation.Description? = null,
        val billCustomization: TokenBillCustomizations? = null,
        val socialLinks: List<SocialLink>? = null,
    ): TokenUpdateRequest

    data class Icon(
        override val token: Token,
        val icon: ModerationAttestation.Image,
    ): TokenUpdateRequest
}
