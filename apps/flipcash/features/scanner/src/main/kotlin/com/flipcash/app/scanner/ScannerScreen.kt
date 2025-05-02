package com.flipcash.app.scanner

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.scanner.internal.Scanner
import dev.theolm.rinku.DeepLink
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
class ScannerScreen(private val deepLink: DeeplinkType? = null) : Screen, Parcelable{
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        Scanner(deepLink)
    }
}