package com.getcode.view.main.getKin

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.KadoWebScreen.BuyKinWebInterface
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.toAGColor
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState

@Composable
fun BoxScope.KadoWebScreen(
    viewModel: KadoWebViewModel,
    state: WebViewState,
    webNavigator: WebViewNavigator,
) {
    val navigator = LocalCodeNavigator.current

    val loadingState = state.loadingState
    if (loadingState is LoadingState.Loading) {
        CodeCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }

    var orderId by remember { mutableStateOf("") }

    val client = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.startsWith("https://app.kado.money/ramp/order/") == true) {
                    // order created, extract order id
                    orderId = Uri.parse(url).lastPathSegment.orEmpty()
                }
            }
        }
    }

    LaunchedEffect(orderId) {
        if (orderId.isNotEmpty()) {
            // order created, start to check status
            viewModel.checkOrderStatus(orderId)
                .onSuccess {
                    TopBarManager.showMessage(
                        "Success! Funds Available Soon",
                       "Your funds should be available in your Code Wallet in 5 to 10 minutes.",
                        type = TopBarManager.TopBarMessageType.SUCCESS
                    )
                    navigator.hide()
                }.onFailure {
                    TopBarManager.showMessage(
                        "Something went wrong",
                        "Your payment method was not charged. Please try again later."
                    )
                    navigator.hide()
                }
        }
    }
    WebView(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        captureBackPresses = false,
        navigator = webNavigator,
        state = state,
        client = client,
        onCreated = { nativeWebView ->
            nativeWebView.addJavascriptInterface(BuyKinWebInterface(), "Android")
            nativeWebView.clipToOutline = true
            nativeWebView.setBackgroundColor(Color.Transparent.toAGColor())
            nativeWebView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
            }
        }
    )
}