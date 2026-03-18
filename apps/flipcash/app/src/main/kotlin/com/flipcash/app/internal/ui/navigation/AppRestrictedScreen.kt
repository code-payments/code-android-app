package com.flipcash.app.internal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.internal.ui.HomeViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.ui.components.restrictions.ContentRestrictedView
import com.getcode.ui.core.RestrictionType
import kotlinx.coroutines.launch

@Composable
fun AppRestrictedScreen(restrictionType: RestrictionType) {
    val homeViewModel = getActivityScopedViewModel<HomeViewModel>()
    val navigator = LocalCodeNavigator.current
    val coroutineScope = rememberCoroutineScope()
    ContentRestrictedView(restrictionType) {
        coroutineScope.launch {
            homeViewModel.logout()
                .onSuccess {
                    navigator.replaceAll(
                        AppRoute.Onboarding.Login()
                    )
                }
        }
    }
}
