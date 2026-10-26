package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flipcash.app.balance.internal.WalletScreen
import com.flipcash.app.balance.internal.WalletViewModel
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull

/** What the Money tab shows beside or over the wallet. */
sealed interface MoneyDetail {
    data class Currency(val mint: Mint) : MoneyDetail
    /** A screen the wallet opens that has no JVM build yet. */
    data class Unavailable(val name: String) : MoneyDetail
}

/**
 * Money (node 12626:56). Compact and medium are one pane, capped at 560 and centred, and a currency
 * opens over the wallet. Expanded keeps the wallet 400 wide and opens a currency in a detail pane
 * beside it (Decisions 12631:459, card 3).
 */
@Composable
fun MoneyPane(
    widthClass: WidthClass,
    walletViewModel: WalletViewModel,
    tokenViewModel: SelectTokenViewModel,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<MoneyDetail?>(null) }

    // The wallet opens a currency through the card-expand overlay, which the Android app root hosts.
    // Desktop shows the currency in a pane instead, so it takes the tapped card's key and drops the
    // expansion before the deck starts reorganising around it.
    val cardExpansion = remember { CardExpansionController().apply { animationScale = { 0f } } }
    LaunchedEffect(cardExpansion) {
        snapshotFlow { cardExpansion.expandedKey }
            .filterNotNull()
            .collect { key ->
                (key as? Mint)?.let { detail = MoneyDetail.Currency(it) }
                cardExpansion.clear()
            }
    }
    LaunchedEffect(walletViewModel) {
        walletViewModel.eventFlow
            .filterIsInstance<WalletViewModel.Event.OpenScreen>()
            .collect { event -> routeToDetail(event.screen)?.let { detail = it } }
    }
    LaunchedEffect(walletViewModel) {
        walletViewModel.eventFlow
            .filterIsInstance<WalletViewModel.Event.OpenCurrencySelection>()
            .collect { detail = MoneyDetail.Unavailable("Region selection") }
    }

    val wallet: @Composable (Modifier) -> Unit = { m ->
        CompositionLocalProvider(LocalCardExpansion provides cardExpansion) {
            Box(m.padding(top = LocalTitleBarInset.current)) {
                WalletScreen(walletViewModel, tokenViewModel)
            }
        }
    }
    val detailPane: @Composable (Modifier, (() -> Unit)?) -> Unit = { m, onBack ->
        Box(m, contentAlignment = Alignment.TopCenter) {
            val shown = detail
            PlaceholderPane(
                title = when (shown) {
                    is MoneyDetail.Currency -> "Currency info"
                    is MoneyDetail.Unavailable -> shown.name
                    null -> "Select a currency"
                },
                blockedBy = when (shown) {
                    is MoneyDetail.Currency -> "Currency info for ${shown.mint} is TokenInfoScreen in " +
                        ":apps:flipcash:shared:tokens androidMain (TokenInfoViewModel and its chart)."
                    is MoneyDetail.Unavailable -> "This screen has no desktop build yet."
                    null -> "A currency's info opens here."
                },
                modifier = Modifier.widthIn(max = DesktopDimens.DetailMaxWidth),
                onBack = onBack,
            )
        }
    }

    when (widthClass) {
        WidthClass.Compact, WidthClass.Medium -> CenteredColumn(DesktopDimens.MoneySinglePaneMaxWidth) {
            if (detail != null) detailPane(Modifier.fillMaxSize()) { detail = null }
            else wallet(Modifier.fillMaxSize())
        }
        WidthClass.Expanded -> Row(modifier.fillMaxSize()) {
            wallet(Modifier.width(DesktopDimens.MoneyListExpanded).fillMaxHeight())
            PaneDivider()
            detailPane(Modifier.weight(1f).fillMaxHeight(), detail?.let { { detail = null } })
        }
    }
}

/** Where a route the wallet opens lands on desktop; null for one desktop drops (the scanner). */
internal fun routeToDetail(route: AppRoute): MoneyDetail? = when (route) {
    is AppRoute.Token.Info -> MoneyDetail.Currency(route.mint)
    is AppRoute.Tabs.Scanner -> null
    else -> MoneyDetail.Unavailable(route::class.simpleName ?: "Screen")
}
