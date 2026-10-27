package com.flipcash.app.tokens.ui

import com.flipcash.services.models.Community
import com.flipcash.services.models.chat.ChatMetadata
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Chats Currency Info previews before "View All". */
const val TopCommunityChatsPreviewCount = 3

private const val QuarksPerToken = 10_000_000_000L

/**
 * The chats a Currency Info page lists, in the server's ranking. A failed call and an empty list
 * both give an empty list, which hides the section.
 */
fun Result<Community>.topCommunityChats(): List<ChatMetadata> =
    getOrNull()?.topHolderChats.orEmpty()

/** The values behind a chat row's "<amount> of <name> held by N members" line. */
data class CommunityChatHoldings(val amount: String, val memberCount: Long)

/**
 * Null when the server sent no holdings for the chat, so the row shows no subtitle rather than a
 * made-up zero.
 */
fun ChatMetadata.communityHoldings(locale: Locale = Locale.getDefault()): CommunityChatHoldings? {
    val summary = holdingsSummary ?: return null
    return CommunityChatHoldings(
        amount = formatQuarksCompact(summary.quarks, locale),
        memberCount = rosterSummary.memberCount,
    )
}

/** A token amount from raw [quarks], abbreviated: 950, 1.2K, 3.4M, 5B. Rounds down. */
fun formatQuarksCompact(quarks: Long, locale: Locale = Locale.getDefault()): String {
    val tokens = BigDecimal(quarks).divide(BigDecimal(QuarksPerToken))
    val (scaled, suffix) = when {
        tokens >= BigDecimal(1_000_000_000) -> tokens.movePointLeft(9) to "B"
        tokens >= BigDecimal(1_000_000) -> tokens.movePointLeft(6) to "M"
        tokens >= BigDecimal(1_000) -> tokens.movePointLeft(3) to "K"
        else -> tokens to ""
    }
    val format = DecimalFormat("#,##0.#", DecimalFormatSymbols(locale)).apply {
        roundingMode = RoundingMode.DOWN
    }
    return format.format(scaled) + suffix
}
