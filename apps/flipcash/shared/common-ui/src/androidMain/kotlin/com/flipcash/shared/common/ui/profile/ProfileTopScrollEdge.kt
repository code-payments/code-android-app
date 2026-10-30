package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.TopScrollEdge
import dev.chrisbanes.haze.HazeState

/**
 * [TopScrollEdge] for a profile, whose cover runs under the status bar and app bar. Like iOS
 * (`hidesTopScrollEdge(untilOffset:)`), it only shows once the cover has scrolled halfway up, so
 * the cover reads clean at rest.
 */
@Composable
fun ProfileTopScrollEdge(
    hazeState: HazeState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    TopScrollEdge(
        hazeState = hazeState,
        scrollState = scrollState,
        height = profileTopScrollEdgeHeight(),
        modifier = modifier,
        threshold = ProfileCoverHeight / 2,
    )
}

/** [ProfileTopScrollEdge] for a profile in a lazy list whose first item is the header. */
@Composable
fun ProfileTopScrollEdge(
    hazeState: HazeState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    TopScrollEdge(
        hazeState = hazeState,
        listState = listState,
        height = profileTopScrollEdgeHeight(),
        modifier = modifier,
        threshold = ProfileCoverHeight / 2,
    )
}

// The status bar and the app bar's row of buttons.
@Composable
private fun profileTopScrollEdgeHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
