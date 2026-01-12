package com.getcode.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

var LocalSheetGesturesState = compositionLocalOf<(Boolean) -> Unit> { {  } }

@Composable
fun DisableSheetGestures(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val sheetGestureState = LocalSheetGesturesState.current
    DisposableEffect(lifecycleOwner) {
        val job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                sheetGestureState(false)
            }
        }
        onDispose {
            job.cancel()
            sheetGestureState(true)
        }
    }
}

@Composable
fun DisableSheetGesturesWhileScrolling(
    scrollState: LazyListState,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val sheetGestureState = LocalSheetGesturesState.current

    val isAtTop by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex == 0 &&
                    scrollState.firstVisibleItemScrollOffset == 0
        }
    }

    var ignoreNextDownward by remember { mutableStateOf(false) }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            ignoreNextDownward = true

            // Auto-reset after a short delay so the user can dismiss with a second drag gesture
            // (makes it feel like confirmation / 2-step without being permanent)
            delay(400)
            ignoreNextDownward = false
        } else {
            // Reset immediately when scrolling away from top
            ignoreNextDownward = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { isAtTop && !ignoreNextDownward }
                    .collect { gesturesEnabled ->
                        sheetGestureState(gesturesEnabled)
                    }
            }
        }
        onDispose {
            job.cancel()
            sheetGestureState(true)
        }
    }
}