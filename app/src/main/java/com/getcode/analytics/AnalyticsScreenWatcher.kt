package com.getcode.analytics

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import com.getcode.manager.AnalyticsManager

@Composable
fun AnalyticsScreenWatcher(
    lifecycleOwner: LifecycleOwner,
    event: AnalyticsManager.Screen,
) {
    AnalyticsWatcher(lifecycleOwner = lifecycleOwner, onEvent = { analytics, context ->
        analytics.open(event)
    })
}