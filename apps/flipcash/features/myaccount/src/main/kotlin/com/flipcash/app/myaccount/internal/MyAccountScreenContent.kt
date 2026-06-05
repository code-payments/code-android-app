package com.flipcash.app.myaccount.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.menu.MenuList

@Composable
internal fun MyAccountScreen(viewModel: MyAccountScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    MyAccountScreenContent(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun MyAccountScreenContent(
    state: MyAccountScreenViewModel.State,
    dispatch: (MyAccountScreenViewModel.Event) -> Unit
) {
    MenuList(
        modifier = Modifier.fillMaxSize(),
        items = state.items,
        showChevrons = true,
        onItemClick = { dispatch(it.action) }
    )
}
