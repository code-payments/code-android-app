package com.flipcash.app.web

import android.os.Parcelable
import androidx.compose.runtime.Composable
import com.getcode.navigation.modal.ModalScreen
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import kotlinx.parcelize.Parcelize

@Parcelize
class WebViewScreen(val url: String): ModalScreen, Parcelable {
    @Composable
    override fun ModalContent() {
        val state = rememberWebViewState(url = url)
        WebView(state = state)
    }
}