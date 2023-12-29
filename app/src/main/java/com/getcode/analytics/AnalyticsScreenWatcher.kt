package com.getcode.analytics

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.manager.AnalyticsManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalContent
import timber.log.Timber

@Composable
fun Screen.AnalyticsScreenWatcher(
    lifecycleOwner: LifecycleOwner,
    event: AnalyticsManager.Screen,
) {
    val navigator = LocalCodeNavigator.current
    val lastItem = navigator.lastItem
    if (lastItem?.key == key) {
        AnalyticsWatcher(
            lifecycleOwner = lifecycleOwner,
            onEvent = { analytics, _ -> analytics.open(event) }
        )
    }
}