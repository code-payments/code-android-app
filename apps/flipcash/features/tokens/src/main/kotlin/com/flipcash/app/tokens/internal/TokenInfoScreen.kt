package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.tokens.internal.components.info.CurrencyInfoContentV2
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.getcode.opencode.model.financial.Fiat
import dev.chrisbanes.haze.HazeState

@Composable
internal fun TokenInfoScreen(
    viewModel: TokenInfoViewModel,
    shortfall: Fiat?,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    // The overlaid app bar is hosted by the outer TokenInfoScreen; content fills behind it, marked as
    // the haze source so the frosted bar chrome frosts it, and inset by [contentPadding].
    CurrencyInfoContentV2(
        shortfall = shortfall,
        state = state,
        listState = listState,
        contentPadding = contentPadding,
        hazeState = hazeState,
        dispatch = viewModel::dispatchEvent,
    )
}
