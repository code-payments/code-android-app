package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.flipcash.app.balance.internal.WalletScreen
import com.flipcash.app.balance.internal.WalletViewModel
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull

/**
 * Wallet in the context pane (node 13000:91): the mobile wallet in one column. A tapped currency card
 * drills to that currency in the same pane, and the wallet's other routes open through [nav].
 */
@Composable
fun WalletPane(
    nav: DesktopNavState,
    walletViewModel: WalletViewModel,
    tokenViewModel: SelectTokenViewModel,
    modifier: Modifier = Modifier,
) {
    // The wallet opens a currency through the card-expand overlay, which the Android app root hosts.
    // Desktop shows the currency in the context pane instead, so it takes the tapped card's key and
    // drops the expansion before the deck starts reorganising around it.
    val cardExpansion = remember { CardExpansionController().apply { animationScale = { 0f } } }
    LaunchedEffect(cardExpansion) {
        snapshotFlow { cardExpansion.expandedKey }
            .filterNotNull()
            .collect { key ->
                (key as? Mint)?.let { nav.openCurrency(it) }
                cardExpansion.clear()
            }
    }
    LaunchedEffect(walletViewModel) {
        walletViewModel.eventFlow
            .filterIsInstance<WalletViewModel.Event.OpenScreen>()
            .collect { event -> nav.openRoute(event.screen) }
    }
    LaunchedEffect(walletViewModel) {
        walletViewModel.eventFlow
            .filterIsInstance<WalletViewModel.Event.OpenCurrencySelection>()
            .collect { nav.context = ContextContent.Unavailable("Region selection") }
    }

    CompositionLocalProvider(LocalCardExpansion provides cardExpansion) {
        Box(modifier) { WalletScreen(walletViewModel, tokenViewModel) }
    }
}
