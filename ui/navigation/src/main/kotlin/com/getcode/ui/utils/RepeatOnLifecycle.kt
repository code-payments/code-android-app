package com.getcode.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RepeatOnLifecycle(
    targetState: Lifecycle.State,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    screen: Screen? = null,
    doOnDispose: () -> Unit = {},
    action: suspend CoroutineScope.() -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val lastItem = navigator.lastItem

    if (screen == null || screen.key == lastItem?.key) {
        DisposableEffect(lifecycleOwner) {
            val job = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(targetState) {
                    action(this)
                }
            }
            onDispose {
                job.cancel()
                doOnDispose()
            }
        }
    }
}