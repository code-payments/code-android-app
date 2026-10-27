package com.flipcash.app.tokens.internal.components.info

/** An entry in Currency Info's action row. The order here is the order on screen. */
internal enum class CurrencyInfoTile { Send, Buy, BuyIn, Transactions, More }

/** A row of the More sheet. The order here is the order on screen. */
internal enum class CurrencyInfoMoreRow { Convert, Withdraw, Deposit }

/**
 * Tiles per state: not held shows Buy In alone; USDF has no Buy; everything else is
 * Send, Buy, Transactions, More.
 */
internal fun currencyInfoTiles(isHeld: Boolean, isUsdf: Boolean): List<CurrencyInfoTile> = when {
    !isHeld -> listOf(CurrencyInfoTile.BuyIn)
    isUsdf -> listOf(CurrencyInfoTile.Send, CurrencyInfoTile.Transactions, CurrencyInfoTile.More)
    else -> listOf(
        CurrencyInfoTile.Send,
        CurrencyInfoTile.Buy,
        CurrencyInfoTile.Transactions,
        CurrencyInfoTile.More,
    )
}

/** USDF is a reserve currency, so it has no per-mint Deposit. */
internal fun currencyInfoMoreRows(isUsdf: Boolean): List<CurrencyInfoMoreRow> =
    if (isUsdf) {
        listOf(CurrencyInfoMoreRow.Convert, CurrencyInfoMoreRow.Withdraw)
    } else {
        listOf(CurrencyInfoMoreRow.Convert, CurrencyInfoMoreRow.Withdraw, CurrencyInfoMoreRow.Deposit)
    }
