package com.flipcash.app.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.discovery.internal.TokenDiscoveryScreen
import com.flipcash.app.discovery.internal.TokenDiscoveryViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TopScrollEdge
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenDiscoveryScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<TokenDiscoveryViewModel>()

    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()

    // The leaderboard runs under the app bar and blurs into it (TopScrollEdge), as iOS's soft
    // scroll edge does, rather than fading out at the bar's bottom edge.
    CodeScaffold(
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            // Sheet-aware app bar: a Close (✕) at the sheet root, a back arrow when pushed deeper.
            AppBarWithTitle(
                title = stringResource(R.string.title_discoverCurrencies),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { navigator.navigateBack() },
            )
        },
    ) { barPadding ->
        val topPadding = barPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            TokenDiscoveryScreen(
                viewModel = viewModel,
                listState = listState,
                hazeState = hazeState,
                topPadding = topPadding,
            )
            TopScrollEdge(hazeState = hazeState, listState = listState, height = topPadding)
        }
    }

    TokenDiscoveryEventHandler(viewModel, navigator)
}

@Composable
private fun TokenDiscoveryEventHandler(viewModel: TokenDiscoveryViewModel, navigator: CodeNavigator) {
    LaunchedEffect(Unit) {
        if (viewModel.stateFlow.value.category == null) {
            viewModel.dispatchEvent(TokenDiscoveryViewModel.Event.OnCategorySelected(DiscoverCategory.Popular))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenDiscoveryViewModel.Event.OpenTokenInfo>()
            .map { it.mint }
            .onEach { navigator.navigate(AppRoute.Token.Info(it, TokenInfoEntry.Discovery)) }
            .launchIn(this)
    }

}