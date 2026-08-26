package com.flipcash.app.tokens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.tokens.internal.TokenInfoScreen
import com.flipcash.app.tokens.internal.components.info.CurrencyInfoTitlePill
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.navigateForResult
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.measured
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenInfoScreen(
    mint: Mint,
    shortFall: Fiat?,
    fromDeeplink: Boolean,
    asPush: Boolean = false,
) {
    val navigator = LocalCodeNavigator.current
    val analytics = rememberAnalytics()
    val viewModel = hiltViewModel<TokenInfoViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // The title is a leading "Liquid Glass" pill that fades in once the hero card's own title has
    // scrolled up under the bar. Approximate that point by the first item's scroll offset.
    val revealThresholdPx = with(LocalDensity.current) { CodeTheme.dimens.staticGrid.x12.toPx() }
    val showPill by remember(listState, revealThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > revealThresholdPx
        }
    }
    val pillProgress by animateFloatAsState(
        targetValue = if (showPill) 1f else 0f,
        label = "titlePill",
    )

    // The app bar chrome (back / title pill / share) is frosted "liquid glass" over the content
    // scrolling beneath it — [haze] is that content's blur source.
    val appBar: @Composable (HazeState?) -> Unit = { haze ->
        AppBarWithTitle(
            titleContent = {
                state.token.dataOrNull?.let { token ->
                    CurrencyInfoTitlePill(
                        token = token,
                        marketCap = state.marketCap,
                        progress = pillProgress,
                        hazeState = haze,
                    )
                }
            },
            titleAlignment = Alignment.Start,
            onBackIconClicked = { navigator.pop() },
            // Currency-info is a modal dismiss, not a true back nav — lead with a close (✕). But when
            // it was PUSHED onto the stack (e.g. drilled into from token discovery) it IS a back nav, so
            // lead with a back arrow instead.
            leadingDismiss = !asPush,
            hazeState = haze,
            endContent = {
                state.token.dataOrNull?.let {
                    if (!state.isCashReserve) {
                        AppBarDefaults.Share(hazeState = haze) {
                            analytics.buttonTapped(Button.TokenShare)
                            viewModel.dispatchEvent(TokenInfoViewModel.Event.Share)
                        }
                    }
                }
            },
        )
    }

    // Overlay: content fills behind the app bar (hazeSource for the frosted chrome) and is inset by
    // the bar height, measured BEFORE the content in the same layout pass (OverlayTopBarScaffold),
    // so the hero card sits correctly on the very first frame — no settle/jump. The bar draws its
    // own bg->transparent scrim (chat-style) so content fades as it scrolls under it.
    val hazeState = rememberHazeState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    OverlayTopBarScaffold(
        topBar = {
            // Fade the status-bar strip plus HALF the app-bar row (behind the chrome): the hero card
            // dims as it scrolls up under the bar (matching iOS) while staying vibrant below the bar's
            // midline. [appBarHeight] is measured on the app bar alone (status bar excluded), so the
            // scrim = status bar + half the app bar. The scrim never grows the content inset (the
            // scaffold measures the full bar), so there's no jump.
            var appBarHeight by remember { mutableStateOf(0.dp) }
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight + appBarHeight * 0.5f)
                        .background(
                            Brush.verticalGradient(
                                0f to CodeTheme.colors.background,
                                1f to Color.Transparent,
                            )
                        )
                )
                Box(modifier = Modifier.statusBarsPadding()) {
                    Box(modifier = Modifier.measured { appBarHeight = it.height }) {
                        appBar(hazeState)
                    }
                }
            }
        },
    ) { topPadding ->
        TokenInfoScreen(
            viewModel = viewModel,
            shortfall = shortFall,
            listState = listState,
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomInset + CodeTheme.dimens.grid.x8,
            ),
            hazeState = hazeState,
        )
    }

    LaunchedEffect(Unit) {
        val source = when {
            shortFall != null -> Analytics.TokenInfoSource.Give
            fromDeeplink -> Analytics.TokenInfoSource.Deeplink
            else -> Analytics.TokenInfoSource.Wallet
        }
        analytics.openTokenInfo(source = source, mint = mint)
    }

    LaunchedEffect(Unit) {
        viewModel.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint, shortFall))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenInfoViewModel.Event.Exit>()
            .onEach { navigator.pop() }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenInfoViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { screen ->
                when (screen) {
                    is AppRoute.Token.Swap -> {
                        navigator.navigateForResult<SwapResult>(screen) { result ->
                            if (result is NavResultOrCanceled.ReturnValue &&
                                result.value is SwapResult.OpenDeposit) {
                                navigator.push(AppRoute.Transfers.Deposit(showOtherOptions = false))
                            }
                        }
                    }
                    else -> navigator.push(screen)
                }
            }.launchIn(this)
    }
}

private enum class OverlaySlot { Bar, Content }

/**
 * Top-bar overlay scaffold: [content] fills the whole area (drawn behind the bar) and is inset from
 * the top by the bar's height, which is measured BEFORE the content in the same layout pass — so the
 * content receives the correct top inset on the very first frame (no settle/jump on open or pop-back).
 * Mirrors the chat screen's ChatInputScaffold, top-bar only.
 */
@Composable
private fun OverlayTopBarScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val barPlaceables = subcompose(OverlaySlot.Bar, topBar).map { it.measure(loose) }
        val barHeight = barPlaceables.maxOfOrNull { it.height } ?: 0

        val contentPlaceables = subcompose(OverlaySlot.Content) { content(barHeight.toDp()) }
            .map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.place(0, 0) }
            barPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, 0) }
        }
    }
}
