package com.flipcash.app.scanner.internal

import com.flipcash.app.core.AppRoute

sealed class ScannerDecorItem(val screen: AppRoute) {
    data object Cash : ScannerDecorItem(AppRoute.Sheets.Cash)
    data object Balance : ScannerDecorItem(AppRoute.Sheets.Balance)
    data object Menu : ScannerDecorItem(AppRoute.Sheets.Menu)
    data object Logo: ScannerDecorItem(AppRoute.Sheets.ShareApp)
    data object Pools : ScannerDecorItem(AppRoute.Sheets.PoolList)
}