package com.getcode.navigation.utils.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RepeatOnLifecycle(
    targetState: Lifecycle.State,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    doOnDispose: () -> Unit = {},
    action: suspend CoroutineScope.() -> Unit,
) {
    DisposableEffect(lifecycleOwner) {
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
