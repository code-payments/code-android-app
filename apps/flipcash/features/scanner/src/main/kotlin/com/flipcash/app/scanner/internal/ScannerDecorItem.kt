package com.flipcash.app.scanner.internal

import com.flipcash.app.core.AppRoute

sealed class ScannerDecorItem(val screen: AppRoute) {
    data object Give : ScannerDecorItem(AppRoute.Sheets.Give)

    data object Wallet : ScannerDecorItem(AppRoute.Sheets.Wallet)
    data object Menu : ScannerDecorItem(AppRoute.Sheets.Menu)
    data object Logo: ScannerDecorItem(AppRoute.Sheets.ShareApp)
}