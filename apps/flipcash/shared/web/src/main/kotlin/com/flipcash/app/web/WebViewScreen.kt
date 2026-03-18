package com.flipcash.app.web

import androidx.compose.runtime.Composable
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState

@Composable
fun WebViewScreen(url: String) {
    val state = rememberWebViewState(url = url)
    WebView(state = state)
}
