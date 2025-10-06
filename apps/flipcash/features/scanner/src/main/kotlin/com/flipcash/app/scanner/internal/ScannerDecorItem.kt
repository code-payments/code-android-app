package com.flipcash.app.scanner.internal

import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint

sealed class ScannerDecorItem(val screen: AppRoute) {
    data object Give : ScannerDecorItem(AppRoute.Main.Give(Mint.usdc))

    data object Wallet : ScannerDecorItem(AppRoute.Sheets.Wallet)
    data object Menu : ScannerDecorItem(AppRoute.Sheets.Menu)
    data object Logo: ScannerDecorItem(AppRoute.Sheets.ShareApp)
}