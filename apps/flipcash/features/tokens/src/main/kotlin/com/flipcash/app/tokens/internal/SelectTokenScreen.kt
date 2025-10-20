package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.tokens.SelectTokenViewModel
import com.flipcash.app.tokens.TokenList

@Composable
internal fun SelectTokenScreen(
    tokenViewModel: SelectTokenViewModel,
) {
    val state by tokenViewModel.stateFlow.collectAsStateWithLifecycle()

    SelectTokenScreenContent(state, tokenViewModel::dispatchEvent)
}

@Composable
private fun SelectTokenScreenContent(
    state: SelectTokenViewModel.State,
    dispatch: (SelectTokenViewModel.Event) -> Unit,
) {
    val tokens = remember(state.tokens) { state.tokens }

    TokenList(
        modifier = Modifier.fillMaxSize(),
        tokens = tokens,
        showFlags = true,
        onTokenSelected = { dispatch(SelectTokenViewModel.Event.OnTokenSelected(it)) }
    )
}