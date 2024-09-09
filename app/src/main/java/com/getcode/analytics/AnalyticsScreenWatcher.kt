package com.getcode.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
fun Screen.AnalyticsScreenWatcher(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    action: Action,
) {
    val navigator = LocalCodeNavigator.current
    val lastItem = navigator.lastItem
    if (lastItem?.key == key) {
        AnalyticsWatcher(
            lifecycleOwner = lifecycleOwner,
            onEvent = { analytics, _ -> analytics.action(action) }
        )
    }
}