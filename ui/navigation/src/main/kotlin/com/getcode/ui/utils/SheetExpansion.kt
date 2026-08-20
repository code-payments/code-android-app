package com.getcode.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets sheet content tell the host sheet whether it overruns the resting detent.
 *
 * A [com.getcode.navigation.HalfSheet] rests at half the screen, so content that fits in that half
 * is already showing everything it has; dragging up would only reveal empty space. The host
 * therefore withholds the expanded detent until content reports `true` here.
 */
val LocalSheetExpansionState = staticCompositionLocalOf<(Boolean) -> Unit> { { } }

/**
 * Reports the host sheet as expandable for as long as [scrollState] has content out of view.
 *
 * A list that can't scroll at the resting detent has nothing to gain from expanding. The report is
 * reset when the content leaves composition so the next sheet starts from its own measurement.
 */
@Composable
fun AllowSheetExpansionWhenScrollable(scrollState: LazyListState) {
    val setExpandable = LocalSheetExpansionState.current

    val hasContentOutOfView by remember(scrollState) {
        derivedStateOf { scrollState.canScrollForward || scrollState.canScrollBackward }
    }

    DisposableEffect(setExpandable, hasContentOutOfView) {
        setExpandable(hasContentOutOfView)
        onDispose { setExpandable(false) }
    }
}
