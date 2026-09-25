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
    // renders as an unsupported message rather than being decoded.
    data object Encrypted : MessageContent
}
