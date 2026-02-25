package com.flipcash.app.web

import android.os.Parcelable
import androidx.compose.runtime.Composable
import com.getcode.navigation.screens.ModalScreen
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class WebViewScreen(val url: String): ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val testTag: String = "webview_screen"

    @Composable
    override fun ModalContent() {
        val state = rememberWebViewState(url = url)
        WebView(state = state)
    }
}