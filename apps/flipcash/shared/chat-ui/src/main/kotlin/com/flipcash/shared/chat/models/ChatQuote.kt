package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID

import com.flipcash.services.models.chat.MediaItemRendition
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
     * The cited sender's colour, used for the rule down the citation's leading edge, or `null` when
     * the message carries no sender id.
     *
     * Nullable because the palette is: `generateComplementaryColorPalette` returns `null` for a
     * message with no id, and the fallback for that case is a theme colour only a composable can
     * read. Resolving the rest here keeps the SHA-512 derivation to once per quote instead of once
     * per frame.
     */
    val accent: Color?,
    /**
     * The next stop of the same sender's palette, used for their name in the composer strip.
     *
     * Two stops rather than one because iOS draws the rule and the name in different colours, and
     * the name sits on the bar's own ground where the rule's darker stop reads as muddy. Both come
     * from the one derivation, so they cannot disagree about whose colour this is.
     */
    val nameAccent: Color?,
    /**
     * The cited sender's id, hex-encoded, kept so the two accents above can be derived again from
     * a citation that was stored and read back rather than mapped from the transcript. Null for a
     * message with no sender id, which is the same case the palette is null for.
     */
    val senderIdHex: String? = null,
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

    /**
     * A quoted photo: a thumbnail beside its caption, or beside the word "Photo" when there is none.
     *
     * @property caption the caption as written, null when the photo has none.
     * @property rendition what a thumbnail is drawn from, null when the item carries none.
     * @property sealed whether the photo's bytes must be opened before they decode.
     * @property redacted a redacted photo is never fetched, so its thumbnail is not drawn.
     */
    data class Photo(
        val caption: String?,
        val rendition: MediaItemRendition?,
        val sealed: Boolean,
        val redacted: Boolean,
        /** Which chat the photo's blob belongs to, for the thumbnail's fetch. */
        val chatId: ChatId? = null,
        /** Whose key a sealed photo opens with; null for the viewer's own. */
        val senderId: ID? = null,
    ) : ChatQuoteSnippet
}
