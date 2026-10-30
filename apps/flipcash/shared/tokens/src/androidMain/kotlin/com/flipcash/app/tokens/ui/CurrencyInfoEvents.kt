package com.flipcash.app.tokens.ui

import com.flipcash.analytics.CurrencyInfoMoreOption
import com.flipcash.analytics.CurrencyInfoTile
import com.flipcash.analytics.events.TokenInfoEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.getcode.solana.keys.Mint

/** `Token Info: Tile Tapped` for the tap, before anything opens. It does not wait for the screen it leads to. */
fun FlipcashAnalytics.trackTileTapped(tile: CurrencyActionTile, mint: Mint) {
    track(TokenInfoEvents.tileTapped(tile.analytics, mint.analytics))
}

/** `Token Info: More Option Picked` for the row picked in the More sheet. */
fun FlipcashAnalytics.trackMoreOptionPicked(row: MoreRow, mint: Mint) {
    track(TokenInfoEvents.moreOptionPicked(row.analytics, mint.analytics))
}

/** `Token Info: Community Chat Tapped` for a row in the Top Community Chats preview or its View All list. */
fun FlipcashAnalytics.trackCommunityChatTapped(mint: Mint) {
    track(TokenInfoEvents.communityChatTapped(mint.analytics))
}

private val CurrencyActionTile.analytics: CurrencyInfoTile
    get() = when (this) {
        CurrencyActionTile.Send -> CurrencyInfoTile.SEND
        CurrencyActionTile.Buy -> CurrencyInfoTile.BUY
        CurrencyActionTile.BuyIn -> CurrencyInfoTile.BUY_IN
        CurrencyActionTile.Transactions -> CurrencyInfoTile.TRANSACTIONS
        CurrencyActionTile.More -> CurrencyInfoTile.MORE
    }

private val MoreRow.analytics: CurrencyInfoMoreOption
    get() = when (this) {
        MoreRow.Convert -> CurrencyInfoMoreOption.CONVERT
        MoreRow.Withdraw -> CurrencyInfoMoreOption.WITHDRAW
        MoreRow.Deposit -> CurrencyInfoMoreOption.DEPOSIT
    }
