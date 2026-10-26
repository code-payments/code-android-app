package com.flipcash.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flipcash.app.analytics.LocalAnalytics
import com.flipcash.app.balance.internal.WalletScreen
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.LocalResources
import com.getcode.utils.network.LocalNetworkObserver

/**
 * The wallet screen with the locals and window background that Android's app root provides
 * (`App.kt` paints [CodeTheme.colors.background] behind every screen). The graph doesn't scope view
 * models, so each read of [DesktopGraph.walletViewModel] builds a new one; they're held here.
 */
@Composable
fun DesktopWalletContent(graph: DesktopGraph) {
    val walletViewModel = remember { graph.walletViewModel }
    val tokenViewModel = remember { graph.selectTokenViewModel }
    LaunchedEffect(tokenViewModel) {
        tokenViewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Balance))
    }
    FlipcashTheme {
        CompositionLocalProvider(
            LocalExchange provides graph.exchange,
            LocalResources provides graph.resources,
            LocalNetworkObserver provides graph.networkObserver,
            LocalAnalytics provides graph.analytics,
        ) {
            Box(Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
                WalletScreen(walletViewModel, tokenViewModel)
            }
        }
    }
}
