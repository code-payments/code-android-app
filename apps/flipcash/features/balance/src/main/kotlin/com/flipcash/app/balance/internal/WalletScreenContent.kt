package com.flipcash.app.balance.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.launch
import com.flipcash.app.core.ui.AppreciationStyle
import com.flipcash.app.core.ui.TokenCardStack
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.balance.internal.components.NewUserTutorial
import com.flipcash.app.balance.internal.components.TutorialItem
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.ui.TileButton
import com.flipcash.app.core.ui.TileButtonStyle
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.features.balance.R
import com.flipcash.shared.transactionhistory.recentActivitySection
import com.getcode.theme.CodeTheme

private const val TokenStackKey = "tokenStack"

@Composable
internal fun WalletScreen(
    viewModel: WalletViewModel,
    tokenViewModel: SelectTokenViewModel,
) {
    val balanceState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()
    WalletScreenContent(
        balanceState = balanceState,
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
internal fun WalletScreenContent(
    balanceState: WalletViewModel.State,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (WalletViewModel.Event) -> Unit,
) {
    val listState = rememberLazyListState()
    // Px the token stack has scrolled above the viewport top, read live so the stack collapses (then
    // releases and scrolls off) as the list scrolls. A lambda so the stack reads it in its placement
    // phase without recomposing.
    val scrolledPast = {
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TokenStackKey }
            ?.let { -it.offset.toFloat() } ?: 0f
    }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val grid = CodeTheme.dimens.grid

    // Card-expand (iOS #587): tapping a card asks the app-level host (LocalCardExpansion) to expand its
    // currency-info as an overlay above this screen. The deck stays composed and reorganises in step with
    // the shared progress scalar — cards part around the tapped one (which is hidden here; the overlay
    // draws the flying hero) and the header fades.
    val cardExpansion = LocalCardExpansion.current
    val expansionScope = rememberCoroutineScope()
    val expandingMint = cardExpansion?.expandedKey as? Mint
    val heroProgress = {
        cardExpansion?.let { CardExpansionController.heroProgress(it.progress.value) } ?: 0f
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.inset,
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            bottom = LocalTabBarPadding.current.calculateBottomPadding() + CodeTheme.dimens.grid.x12,
        ),
    ) {
        item {
            // v2 wallet header: 96 dp top / 44 dp bottom per Figma node 8966:1578.
            BalanceHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fade the balance out as the deck parts behind the opening card (iOS deckOpacity).
                    .graphicsLayer { alpha = 1f - heroProgress() },
                balance = tokenState.totalBalance,
                appreciation = tokenState.aggregateAppreciation,
                topPadding = 96.dp,
                bottomPadding = 44.dp,
                appreciationStyle = AppreciationStyle.Pill,
            ) {
                dispatchEvent(WalletViewModel.Event.OpenCurrencySelection)
            }
        }

        item {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
        }

        if (!balanceState.isNewUserTutorialComplete) {
            item {
                NewUserTutorial(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = CodeTheme.dimens.grid.x5),
                    title = stringResource(R.string.title_tipOnboarding),
                    items = balanceState.onboardingItems,
                ) { item ->
                    when (item) {
                        is TutorialItem.AddMoney -> {
                            dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
                        }
                        is TutorialItem.ScanTipCard -> {
                            dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Main.Scanner))
                        }
                    }
                }
            }
        }

        tokenState.tokens?.takeIf { it.isNotEmpty() }?.let { tokens ->
            item(key = TokenStackKey) {
                TokenCardStack(
                    tokens = tokens,
                    modifier = Modifier.fillMaxWidth(),
                    pinInset = statusBarInset + CodeTheme.dimens.grid.x2,
                    scrolledPast = scrolledPast,
                    expandingMint = expandingMint,
                    expandProgress = heroProgress,
                    heroTarget = cardExpansion?.heroBounds,
                    pullOffset = { cardExpansion?.pullOffset ?: 0f },
                    onCardClick = { token, bounds ->
                        // Expand the tapped card's currency-info as an overlay (app-level host draws it);
                        // the deck reorganises here in step with the shared progress scalar.
                        cardExpansion?.let { controller ->
                            controller.begin(token.token.address, bounds)
                            expansionScope.launch {
                                // Snap to a fully-collapsed deck first: tapping a card while a prior one is
                                // still collapsing would otherwise open from that card's mid-progress, so the
                                // whole deck (including the one just closed) jumps into a half-reorganised
                                // pose and animates from there ("stacked" enter animations).
                                controller.snapTo(0f)
                                controller.animateTo(1f)
                            }
                        }
                    },
                )
            }
        }

        if (balanceState.transactions.isNotEmpty()) {
            // Preview of the most recent activity (newest first); the full paged history lives on its own
            // screen. Tap the header to dive in. Shared with the token-info screen for consistency.
            recentActivitySection(
                transactions = balanceState.transactions,
                modifier = Modifier
                    .padding(top = grid.x2)
                    .clickable {
                        dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Sheets.ActivityHistory))
                    }
                    .padding(top = grid.x4, bottom = grid.x1),
            )
        }

        if (balanceState.hasAddedMoney) {
            item {
                Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
            }

            item {
                // 2x2 action grid. Fixed, width-proportional tile height (like iOS) so tiles are
                // equal height and tall enough for the icon (top) and label (bottom) to sit apart —
                // labels bottom-align even when one wraps to two lines.
                Column(
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        // ic_menu_deposit/ic_menu_withdraw are the app's canonical glyphs for this
                        // pair (the Settings-sheet money tiles) — matched arrows, not ad-hoc icons.
                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(R.string.action_addMoney),
                            icon = painterResource(R.drawable.ic_menu_deposit),
                        ) {
                            dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
                        }

                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(R.string.action_withdrawMoney),
                            icon = painterResource(R.drawable.ic_menu_withdraw),
                        ) {
                            // No preselected mint: the flow opens on the currency picker, which lists
                            // every balance (Dollars included).
                            dispatchEvent(
                                WalletViewModel.Event.OpenScreen(
                                    AppRoute.Transfers.Withdrawal(preselectedMint = null)
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(R.string.action_discoverCurrencies),
                            icon = painterResource(R.drawable.ic_globe),
                        ) {
                            dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Token.Discovery))
                        }

                        // Currency creation now lives here rather than as a Discover promo.
                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(R.string.action_createCurrency),
                            icon = painterResource(R.drawable.ic_coins_add),
                        ) {
                            dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Token.CurrencyCreator))
                        }
                    }
                }
            }
        }
    }
}