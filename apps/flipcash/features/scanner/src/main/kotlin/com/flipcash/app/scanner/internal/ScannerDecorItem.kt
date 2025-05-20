package com.flipcash.app.scanner.internal

import com.flipcash.app.core.NavScreenProvider

sealed class ScannerDecorItem(val screen: NavScreenProvider) {
//    data object Give : ScannerDecorItem(NavScreenProvider.HomeScreen.Give)
    data object Cash : ScannerDecorItem(NavScreenProvider.HomeScreen.Cash)
//    data object Send : ScannerDecorItem(NavScreenProvider.HomeScreen.Send)
    data object Balance : ScannerDecorItem(NavScreenProvider.HomeScreen.Balance)
    data object Menu : ScannerDecorItem(NavScreenProvider.HomeScreen.Menu.Root)
    data object Logo: ScannerDecorItem(NavScreenProvider.HomeScreen.ShareApp)
}