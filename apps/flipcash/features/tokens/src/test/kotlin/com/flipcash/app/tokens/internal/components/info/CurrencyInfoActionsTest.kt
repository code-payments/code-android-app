package com.flipcash.app.tokens.internal.components.info

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyInfoActionsTest {
    @Test
    fun `held non-usdf shows send buy transactions more`() {
        assertEquals(
            listOf(
                CurrencyInfoTile.Send,
                CurrencyInfoTile.Buy,
                CurrencyInfoTile.Transactions,
                CurrencyInfoTile.More,
            ),
            currencyInfoTiles(isHeld = true, isUsdf = false),
        )
    }

    @Test
    fun `held usdf has no buy`() {
        assertEquals(
            listOf(CurrencyInfoTile.Send, CurrencyInfoTile.Transactions, CurrencyInfoTile.More),
            currencyInfoTiles(isHeld = true, isUsdf = true),
        )
    }

    @Test
    fun `not held shows buy in only`() {
        assertEquals(listOf(CurrencyInfoTile.BuyIn), currencyInfoTiles(isHeld = false, isUsdf = false))
        assertEquals(listOf(CurrencyInfoTile.BuyIn), currencyInfoTiles(isHeld = false, isUsdf = true))
    }

    @Test
    fun `more rows are convert, withdraw and deposit for every currency`() {
        assertEquals(
            listOf(CurrencyInfoMoreRow.Convert, CurrencyInfoMoreRow.Withdraw, CurrencyInfoMoreRow.Deposit),
            currencyInfoMoreRows(isUsdf = false),
        )
        assertEquals(
            listOf(CurrencyInfoMoreRow.Convert, CurrencyInfoMoreRow.Withdraw, CurrencyInfoMoreRow.Deposit),
            currencyInfoMoreRows(isUsdf = true),
        )
    }
}
