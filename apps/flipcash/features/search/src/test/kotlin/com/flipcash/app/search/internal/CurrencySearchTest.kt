package com.flipcash.app.search.internal

import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MarketCapMetrics
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.WindowedRange
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencySearchTest {

    private data class Currency(val mint: String, val name: String, val symbol: String)

    private fun filter(query: String, vararg candidates: Currency) = filterByNameOrSymbol(
        candidates = candidates.toList(),
        query = SearchMatching.query(query)!!,
        id = { it.mint },
        name = { it.name },
        symbol = { it.symbol },
    ).map { it.mint }

    @Test
    fun `currencies match on name or symbol, ignoring case and accents`() {
        assertEquals(
            listOf("a", "b"),
            filter(
                "cafe",
                Currency("a", "Café Coin", "CC"),
                Currency("b", "Beans", "CAFE"),
                Currency("c", "Tea", "TEA"),
            ),
        )
    }

    @Test
    fun `a currency both held and in Discover shows once, in its first place`() {
        assertEquals(
            listOf("a", "b"),
            filter(
                "coin",
                Currency("a", "Jeff Coin", "JEFF"),
                Currency("b", "Bob Coin", "BOB"),
                Currency("a", "Jeff Coin", "JEFF"),
            ),
        )
    }

    private fun token(
        holders: List<HolderMetrics.HolderDelta> = emptyList(),
        marketCap: List<MarketCapMetrics.MarketCapDelta> = emptyList(),
    ): Token = mockk {
        every { marketCap() } returns null
        every { holderMetrics } returns HolderMetrics(10, holders)
        every { marketCapMetrics } returns MarketCapMetrics(100.0, marketCap)
    }

    @Test
    fun `a currency Discover gave no metrics shows neither weekly line`() {
        val figures = currencyCardFigures(token())
        assertNull(figures.weeklyChange)
        assertNull(figures.newMembers)
    }

    @Test
    fun `the weekly change shows even when it is a loss`() {
        val figures = currencyCardFigures(
            token(marketCap = listOf(MarketCapMetrics.MarketCapDelta(WindowedRange.LastWeek, -7.0))),
        )
        assertEquals(-7.0, figures.weeklyChange)
    }

    @Test
    fun `new members show only when the week added some`() {
        val week = { delta: Long -> listOf(HolderMetrics.HolderDelta(WindowedRange.LastWeek, delta)) }
        assertEquals(4L, currencyCardFigures(token(holders = week(4))).newMembers)
        assertNull(currencyCardFigures(token(holders = week(0))).newMembers)
        assertNull(currencyCardFigures(token(holders = week(-2))).newMembers)
    }
}
