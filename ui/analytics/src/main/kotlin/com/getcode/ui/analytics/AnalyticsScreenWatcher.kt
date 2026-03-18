package com.getcode.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.getcode.libs.analytics.AppAction
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
fun AnalyticsScreenWatcher(
    route: NavKey,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    action: AppAction,
) {
    val navigator = LocalCodeNavigator.current
    val lastItem = navigator.lastItem
    if (lastItem == route) {
        AnalyticsWatcher(
            lifecycleOwner = lifecycleOwner,
            onEvent = { analytics, _ -> analytics.action(action) }
        )
    }
}
