package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.time.Instant

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Cash(
        val intentId: ID,
        val amount: Fiat,
        val mint: Mint,
        val tokenName: String = "",
        val tokenImageUrl: String = "",
        val action: Action = Action.SENT,
    ) : MessageContent {
        enum class Action {
            SENT,
            TIPPED,
        }
    }
    data class Reply(
        val repliedMessageId: Long,
        val content: List<MessageContent>,
    ) : MessageContent
    data class Media(
        val items: List<MediaItem>,
        val caption: Text?,
    ) : MessageContent
    data class System(val fallbackText: String) : MessageContent
    data class Deleted(
        val deletedTs: Instant,
        val deletedBy: ID?,
    ) : MessageContent
    // Placeholder for `messaging.v1.Content.encrypted` (DMs only). Crypto (X25519/HKDF/
    // XChaCha20) is a cross-platform parity hotspot and needs its own decision; until then this
    // renders as an unsupported message rather than being decoded. The raw fields are kept
    // verbatim (not decrypted) so the ciphertext survives persistence and can be faithfully
    // re-encoded, rather than being lost the moment it is stored locally.
    data class Encrypted(
        val scheme: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    ) : MessageContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Encrypted) return false
            return scheme == other.scheme &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int {
            var result = scheme
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }

        override fun toString(): String =
            "Encrypted(scheme=$scheme, nonce=${nonce.size} bytes, ciphertext=${ciphertext.size} bytes)"
    }
}
