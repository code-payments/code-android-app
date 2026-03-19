package com.flipcash.app.web

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ComposeWebView(
    url: String,
    modifier: Modifier = Modifier,
    factoryExtension: WebView.() -> Unit = { },
) {
    AndroidView(
        modifier = modifier,
        factory = { WebView(it).apply(factoryExtension) },
        update = { it.loadUrl(url) }
    )
}
