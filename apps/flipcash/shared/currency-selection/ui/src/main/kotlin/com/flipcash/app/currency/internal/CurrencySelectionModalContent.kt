package com.flipcash.app.currency.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.currency.internal.components.CurrencyList
import com.flipcash.app.currency.internal.components.SearchBar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
internal fun CurrencySelectionModalContent(viewModel: CurrencyViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val keyboard = rememberKeyboardController()
    val animationScale by rememberAnimationScale()


    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyViewModel.Event.OnSelectedCurrencyChanged>()
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
        LaunchedEffect(Unit) {
            snapshotFlow { state.searchState.text }
                .launchIn(this)
        }
        SearchBar(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            state = state.searchState
        )

        CurrencyList(
            modifier = Modifier.weight(1f),
            items = state.listItems,
            isLoading = state.loading,
            selected = state.selectedCurrency,
            onRemoved = { currency ->
                viewModel.dispatchEvent(CurrencyViewModel.Event.OnRecentCurrencyRemoved(currency))
            },
            onSelected = { currency ->
                viewModel.dispatchEvent(CurrencyViewModel.Event.OnCurrencySelected(currency))
            }
        )
    }
}