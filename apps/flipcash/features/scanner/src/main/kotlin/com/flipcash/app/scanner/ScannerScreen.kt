package com.flipcash.app.scanner

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.scanner.internal.Scanner
import com.getcode.navigation.screens.AppScreen
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ScannerScreen(private val deepLink: DeeplinkType? = null) : AppScreen, Parcelable{
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "scanner_screen"

    @Composable
    override fun ScreenContent() {
        Scanner(deepLink)
    }
}