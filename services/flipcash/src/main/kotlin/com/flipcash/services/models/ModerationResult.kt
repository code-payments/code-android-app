package com.flipcash.services.models

import com.getcode.crypt.Sha256Hash
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlin.time.Instant

sealed interface ModerationResult {
    val isAllowed: Boolean
    val attestation: Attestation
    val flaggedCategory: FlaggedCategory


    /**
     * Signed proof of the moderation result.
     * The signature is computed over this message without the signature field set.
     */
    data class Attestation(
        /** SHA-256 hash of the moderated content to be allowed */
        val hash: Sha256Hash,
        /** Timestamp of the moderation */
        val timestamp: Instant,
        /** The user who submitted the content */
        val userId: ID,
        /** Public key of the attestor that signed this message */
        val attestor: PublicKey,
        /** Attestor signature over this message */
        val signature: Signature,
    )

    enum class FlaggedCategory {
        NONE,          // 0
        OTHER,         // 1 Fallback category when flagged content does not fit into a well-defined FlaggedCategory
        NSFW,          // 2
        IMPERSONATION, // 3
        MISLEADING,    // 4
        SPAM,          // 5
    }

    data class Text(
        val text: String,
        override val isAllowed: Boolean,
        override val attestation: Attestation,
        override val flaggedCategory: FlaggedCategory
    ): ModerationResult

    data class Image(
        val image: List<Byte>,
        override val isAllowed: Boolean,
        override val attestation: Attestation,
        override val flaggedCategory: FlaggedCategory
    ): ModerationResult
}
