package com.flipcash.app.messenger.internal

import com.flipcash.shared.chat.ChatDraftReply
import com.flipcash.shared.chat.ChatDraftSnippet
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.utils.generateComplementaryColorPalette

/**
 * The composer's reply strip, translated between the shape the screen renders and the shape the
 * draft store keeps.
 *
 * The translation lives in this module because this is the only one that can see both: the store is
 * in `:shared:chat`, which has no dependency on the chat UI's models, and the accents are derived
 * from a palette that only the UI layer owns.
 */
internal fun ChatQuote.toDraftReply(): ChatDraftReply = ChatDraftReply(
    messageId = messageId,
    authorName = authorName,
    senderIdHex = senderIdHex,
    snippet = when (val snippet = snippet) {
        is ChatQuoteSnippet.Text -> ChatDraftSnippet.Text(snippet.body)
        is ChatQuoteSnippet.Cash -> ChatDraftSnippet.Cash(
            quarks = snippet.amount.quarks,
            currencyCode = snippet.amount.currencyCode.name,
            tokenName = snippet.tokenName,
        )
    },
)

/**
 * Rebuilds the strip from what was stored, rather than from the transcript.
 *
 * A restored draft is read before any history is, so the cited message may not be in the loaded
 * window — or in the database at all, for a chat whose sync has not run yet. The accents are
 * re-derived from the sender id because the palette is a pure function of it; a citation stored
 * without one comes back with the same null accents it had.
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun ChatDraftReply.toChatQuote(): ChatQuote {
    val palette = senderIdHex?.let { hex ->
        runCatching { hex.hexToByteArray().toList() }.getOrNull()
            ?.let { generateComplementaryColorPalette(it) }
    }
    return ChatQuote(
        messageId = messageId,
        authorName = authorName,
        snippet = when (val snippet = snippet) {
            is ChatDraftSnippet.Text -> ChatQuoteSnippet.Text(snippet.body)
            is ChatDraftSnippet.Cash -> ChatQuoteSnippet.Cash(
                amount = Fiat(
                    quarks = snippet.quarks,
                    currencyCode = CurrencyCode.tryValueOf(snippet.currencyCode) ?: CurrencyCode.USD,
                ),
                tokenName = snippet.tokenName,
            )
        },
        accent = palette?.first,
        nameAccent = palette?.second,
        senderIdHex = senderIdHex,
    )
}
