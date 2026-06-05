package com.flipcash.app.currency.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun RegionSelectionScreen(viewModel: RegionSelectionViewModel) {
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
                    delay(500.scaled(animationScale))
                }
                navigator.pop()
            }.launchIn(this)
    }

    Column(
        modifier = Modifier.imePadding(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        SearchInput(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.grid.x3)
                .padding(top = CodeTheme.dimens.grid.x3),
            state = state.searchState,
            contentPadding = PaddingValues(start = CodeTheme.dimens.grid.x1),
            placeholder = stringResource(R.string.subtitle_searchRegions)
        )

        RegionList(
            modifier = Modifier.weight(1f),
            items = state.listItems,
            selected = state.selectedCurrency,
            onRemoved = { currency ->
                viewModel.dispatchEvent(RegionSelectionViewModel.Event.OnRecentCurrencyRemoved(currency))
            },
            onSelected = { currency ->
                viewModel.dispatchEvent(RegionSelectionViewModel.Event.OnCurrencySelected(currency))
            }
        )
    }
}