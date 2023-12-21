package com.getcode.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@Composable
fun RepeatOnLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    key: Any? = null,
    targetState: Lifecycle.State,
    doOnDispose: () -> Unit = {},
    action: suspend () -> Unit,
) {
    DisposableEffect(lifecycleOwner, key) {
        val job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(targetState) {
                action()
            }
        }
        onDispose {
            job.cancel()
            doOnDispose()
        }
    }
}
