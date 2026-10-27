package com.flipcash.app.tokens.ui

import com.flipcash.app.core.ui.forViewer
import com.flipcash.services.models.Community
import com.flipcash.services.models.chat.ChatMetadata
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token

/** Chats Currency Info previews before "View All". */
const val TopCommunityChatsPreviewCount = 3

/**
 * The chats a Currency Info page lists, in the server's ranking. A failed call and an empty list
 * both give an empty list, which hides the section.
 */
fun Result<Community>.topCommunityChats(): List<ChatMetadata> =
    getOrNull()?.topHolderChats.orEmpty()

/**
 * What a chat row's "<amount> of <name> held by N members" line shows: [amount] is the fiat value
 * of the chat's holdings in the viewer's currency, or null when the line is just "N members".
 */
data class CommunityChatHoldings(val amount: String?, val memberCount: Long)

/**
 * The holdings quarks valued on the bonding curve at [token]'s current supply, then shown in the
 * viewer's currency like the hero balance. Null amount when the server sent no holdings or [token]
 * is not known, matching iOS, which still shows the member count.
 */
fun ChatMetadata.communityHoldings(
    token: Token?,
    preferredRate: Rate,
    rates: Map<CurrencyCode, Rate>,
): CommunityChatHoldings {
    val amount = holdingsSummary?.let { summary ->
        token ?: return@let null
        runCatching { Fiat.tokenBalance(summary.quarks, token) }.getOrNull()
            ?.forViewer(preferredRate, rates)
            ?.formatted(Fiat.FormattingRule.Truncated)
    }
    return CommunityChatHoldings(amount = amount, memberCount = rosterSummary.memberCount)
}

/** A ranked chat with the line its row shows. */
data class CommunityChatEntry(val chat: ChatMetadata, val holdings: CommunityChatHoldings)

fun List<ChatMetadata>.asEntries(
    token: Token?,
    preferredRate: Rate,
    rates: Map<CurrencyCode, Rate>,
): List<CommunityChatEntry> = map { CommunityChatEntry(it, it.communityHoldings(token, preferredRate, rates)) }
