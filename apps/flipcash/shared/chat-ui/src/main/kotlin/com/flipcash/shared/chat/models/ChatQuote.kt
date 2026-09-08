package com.flipcash.shared.chat.models

import androidx.compose.ui.graphics.Color
import com.getcode.opencode.model.financial.Fiat

/**
 * A citation of another message, rendered identically by the composer strip and by the panel inside
 * a sent bubble.
 *
 * Resolved once, where the transcript is mapped, rather than by each surface: the original has to be
 * looked up in the local database either way, and two lookups would let the strip and the bubble
 * disagree about the same message.
 */
data class ChatQuote(
    /** The cited message, which is what a tap on the panel scrolls to. */
    val messageId: Long,
    val authorName: String,
    val snippet: ChatQuoteSnippet,
    /**
     * The cited sender's colour, or `null` when the message carries no sender id.
     *
     * Nullable because the palette is: `generateComplementaryColorPalette` returns `null` for a
     * message with no id, and the fallback for that case is a theme colour only a composable can
     * read. Resolving the rest here keeps the SHA-512 derivation to once per quote instead of once
     * per frame.
     */
    val accent: Color?,
)

/** What a quote shows of the message it cites. */
sealed interface ChatQuoteSnippet {
    data class Text(val body: String) : ChatQuoteSnippet

    /**
     * A quoted payment shows its currency flag, amount and token name rather than a bare number,
     * matching what iOS shipped: the amount alone does not identify which payment is being
     * discussed.
     */
    data class Cash(val amount: Fiat, val tokenName: String) : ChatQuoteSnippet
}
