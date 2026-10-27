package com.flipcash.app.tokens.ui

import com.getcode.solana.keys.Mint

/** A tile in Currency Info's action row. */
enum class CurrencyActionTile { Send, Buy, BuyIn, Transactions, More }

/**
 * The action row for a currency: a held currency sends, buys more (not Dollars, which has no
 * buy), lists its transactions and opens More. One that isn't held offers only Buy In.
 */
fun currencyActionTiles(mint: Mint, isHeld: Boolean): List<CurrencyActionTile> = when {
    !isHeld -> listOf(CurrencyActionTile.BuyIn)
    mint == Mint.usdf -> listOf(
        CurrencyActionTile.Send,
        CurrencyActionTile.Transactions,
        CurrencyActionTile.More,
    )
    else -> listOf(
        CurrencyActionTile.Send,
        CurrencyActionTile.Buy,
        CurrencyActionTile.Transactions,
        CurrencyActionTile.More,
    )
}

/** A row in the More sheet. */
enum class MoreRow { Convert, Withdraw, Deposit }

/** Dollars has no Deposit row: its deposit is the account's own, not a per-currency one. */
fun moreRows(mint: Mint): List<MoreRow> =
    if (mint == Mint.usdf) listOf(MoreRow.Convert, MoreRow.Withdraw)
    else listOf(MoreRow.Convert, MoreRow.Withdraw, MoreRow.Deposit)
