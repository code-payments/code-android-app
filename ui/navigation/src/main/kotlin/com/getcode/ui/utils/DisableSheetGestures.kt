package com.getcode.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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