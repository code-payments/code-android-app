package com.flipcash.app.tokens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
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
) {
    val navigator = LocalCodeNavigator.current
    val analytics = rememberAnalytics()
    val viewModel = hiltViewModel<TokenInfoViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val features = LocalFeatureFlags.current
    val isNewUi = remember(features) { features.observe(FeatureFlag.NewUi).value }
    val listState = rememberLazyListState()

    // v2: the title is a leading "Liquid Glass" pill that fades in once the hero card's own title has
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

    // For v2 the app bar chrome (back / title pill / share) is frosted "liquid glass" over the content
    // scrolling beneath it — [haze] is that content's blur source.
    val appBar: @Composable (HazeState?) -> Unit = { haze ->
        AppBarWithTitle(
            titleContent = {
                state.token.dataOrNull?.let { token ->
                    if (isNewUi) {
                        CurrencyInfoTitlePill(
                            token = token,
                            marketCap = state.marketCap,
                            progress = pillProgress,
                            hazeState = haze,
                        )
                    } else {
                        TokenIconWithName(
                            token = token,
                            imageSize = CodeTheme.dimens.staticGrid.x5,
                            spacing = CodeTheme.dimens.grid.x1,
                        )
                    }
                }
            },
            titleAlignment = if (isNewUi) Alignment.Start else Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.pop() },
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

    if (isNewUi) {
        // Chat-style overlay: content fills behind the app bar (hazeSource for the frosted chrome),
        // inset below it by the measured bar height; the bar draws a bg->transparent scrim so content
        // fades softly as it scrolls under it.
        val hazeState = rememberHazeState()
        var barHeight by remember { mutableStateOf(0.dp) }
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            TokenInfoScreen(
                viewModel = viewModel,
                shortfall = shortFall,
                listState = listState,
                contentPadding = PaddingValues(
                    top = barHeight,
                    bottom = bottomInset + CodeTheme.dimens.grid.x8,
                ),
                hazeState = hazeState,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight + CodeTheme.dimens.grid.x5)
                    .background(
                        Brush.verticalGradient(
                            0f to CodeTheme.colors.background,
                            1f to Color.Transparent,
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .measured { barHeight = it.height },
            ) {
                appBar(hazeState)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            appBar(null)
            TokenInfoScreen(viewModel, shortFall, listState)
        }
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
