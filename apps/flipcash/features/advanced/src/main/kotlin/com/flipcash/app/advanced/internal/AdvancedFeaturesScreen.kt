package com.flipcash.app.advanced.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.menu.MenuList

@Composable
internal fun AdvancedFeaturesScreen(viewModel: AdvancedFeaturesScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    AdvancedFeaturesScreenContent(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun AdvancedFeaturesScreenContent(
    state: AdvancedFeaturesScreenViewModel.State,
    dispatch: (AdvancedFeaturesScreenViewModel.Event) -> Unit
) {
    MenuList(
        modifier = Modifier.fillMaxSize(),
        items = state.items,
        showChevrons = true,
        onItemClick = { dispatch(it.action) }
    )
}