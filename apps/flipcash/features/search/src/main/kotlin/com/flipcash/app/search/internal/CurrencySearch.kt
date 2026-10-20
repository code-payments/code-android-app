package com.flipcash.app.search.internal

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.WindowedRange

/**
 * Finds currencies by name. There is no server-side name search yet, so [LocalCurrencySearch]
 * filters what the app already has; a server RPC can take its place behind this interface.
 */
internal interface CurrencySearch {
    /** Loads anything slow ahead of the first query, so typing never waits on the network. */
    suspend fun warm() = Unit

    /** Currencies whose name or symbol contains [query] (already folded). */
    suspend fun search(query: String): List<Token>
}

/**
 * The items in [candidates] whose name or symbol contains [query] (already folded), one per [id],
 * in candidate order.
 */
internal fun <T> filterByNameOrSymbol(
    candidates: List<T>,
    query: String,
    id: (T) -> Any,
    name: (T) -> String,
    symbol: (T) -> String,
): List<T> = candidates
    .distinctBy(id)
    .filter { SearchMatching.matches(query, name(it), symbol(it)) }

/**
 * What a currency card shows beyond its name. The weekly change and new members only come from
 * Discover's metrics, so a held currency Discover didn't return shows its market cap alone.
 */
internal data class CurrencyCardFigures(
    val marketCap: Fiat?,
    val weeklyChange: Double?,
    val newMembers: Long?,
)

internal fun currencyCardFigures(token: Token): CurrencyCardFigures = CurrencyCardFigures(
    marketCap = token.marketCap(),
    weeklyChange = token.marketCapMetrics.marketCapDeltas
        .firstOrNull { it.range == WindowedRange.LastWeek }?.delta,
    newMembers = token.holderMetrics.deltaForWindow(WindowedRange.LastWeek).takeIf { it > 0 },
)
