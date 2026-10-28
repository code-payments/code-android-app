package com.flipcash.app.tokens.ui

import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyActionsTest {
    private val jeffy = Mint(List(32) { 7.toByte() })

    @Test
    fun `held currency sends, buys, lists transactions and opens more`() {
        assertEquals(
            listOf(
                CurrencyActionTile.Send,
                CurrencyActionTile.Buy,
                CurrencyActionTile.Transactions,
                CurrencyActionTile.More,
            ),
            currencyActionTiles(jeffy, isHeld = true),
        )
    }

    @Test
    fun `dollars has no buy tile`() {
        assertEquals(
            listOf(CurrencyActionTile.Send, CurrencyActionTile.Transactions, CurrencyActionTile.More),
            currencyActionTiles(Mint.usdf, isHeld = true),
        )
    }

    @Test
    fun `a currency that is not held offers only buy in`() {
        assertEquals(listOf(CurrencyActionTile.BuyIn), currencyActionTiles(jeffy, isHeld = false))
        assertEquals(listOf(CurrencyActionTile.BuyIn), currencyActionTiles(Mint.usdf, isHeld = false))
    }

    @Test
    fun `more lists convert, withdraw and deposit for every currency`() {
        assertEquals(listOf(MoreRow.Convert, MoreRow.Withdraw, MoreRow.Deposit), moreRows(jeffy))
        assertEquals(listOf(MoreRow.Convert, MoreRow.Withdraw, MoreRow.Deposit), moreRows(Mint.usdf))
    }
}
