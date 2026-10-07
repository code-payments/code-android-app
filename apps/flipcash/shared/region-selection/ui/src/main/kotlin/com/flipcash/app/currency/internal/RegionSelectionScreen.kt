package com.flipcash.app.currency.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.currency.internal.components.RegionList
import com.flipcash.features.currency.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SearchInput
import com.getcode.ui.components.TopScrollEdge
import com.getcode.ui.components.glass.floatingGlass
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RegionSelectionScreen(
    viewModel: RegionSelectionViewModel,
    appBar: @Composable () -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val keyboard = rememberKeyboardController()
    val animationScale by rememberAnimationScale()


    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<RegionSelectionViewModel.Event.OnSelectedCurrencyChanged>()
            .onEach {
                if (keyboard.visible) {
                    keyboard.hide()
                    delay(500.scaled(animationScale).milliseconds)
                }
                navigator.pop()
            }.launchIn(this)
    }

    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()

    // The list runs under the app bar and search field and blurs into them (TopScrollEdge), as
    // iOS's soft scroll edge does, rather than fading out below the search field.
    CodeScaffold(
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            Column {
                appBar()
                SearchInput(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.grid.x3)
                        .padding(top = CodeTheme.dimens.grid.x3)
                        // Frosts the rows passing behind it, as the featured-groups picker's does.
                        .floatingGlass(hazeState),
                    state = state.searchState,
                    contentPadding = PaddingValues(start = CodeTheme.dimens.grid.x1),
                    placeholder = stringResource(R.string.subtitle_searchRegions)
                )
            }
        },
    ) { barPadding ->
        val topPadding = barPadding.calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            RegionList(
                modifier = Modifier.hazeSource(hazeState),
                listState = listState,
                topPadding = topPadding + CodeTheme.dimens.grid.x2,
                items = state.listItems,
                selected = state.selectedCurrency,
                onRemoved = { currency ->
                    viewModel.dispatchEvent(RegionSelectionViewModel.Event.OnRecentCurrencyRemoved(currency))
                },
                onSelected = { currency ->
                    viewModel.dispatchEvent(RegionSelectionViewModel.Event.OnCurrencySelected(currency))
                }
            )
            TopScrollEdge(hazeState = hazeState, listState = listState, height = topPadding)
        }
    }
}