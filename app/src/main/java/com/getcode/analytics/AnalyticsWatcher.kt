package com.getcode.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.getcode.LocalAnalytics
import com.getcode.manager.AnalyticsService
import com.getcode.util.RepeatOnLifecycle

@Composable
fun AnalyticsWatcher(
    lifecycleOwner: LifecycleOwner,
    onEvent: (AnalyticsService, Context) -> Unit,
    onDispose: (AnalyticsService, Context) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val analyticsService = LocalAnalytics.current

    val updatedOnEvent by rememberUpdatedState(newValue = onEvent)
    val updatedOnDispose by rememberUpdatedState(newValue = onDispose)

    RepeatOnLifecycle(
        lifecycleOwner = lifecycleOwner,
        targetState = Lifecycle.State.RESUMED,
        doOnDispose = {
            updatedOnDispose(analyticsService, context)
        },
    ) {
        updatedOnEvent(analyticsService, context)
    }
}