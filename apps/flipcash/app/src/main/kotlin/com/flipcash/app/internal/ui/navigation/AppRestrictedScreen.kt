package com.flipcash.app.internal.ui.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.internal.ui.HomeViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.AppScreen
import com.getcode.ui.components.restrictions.ContentRestrictedView
import com.getcode.ui.core.RestrictionType
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class AppRestrictedScreen(private val restrictionType: RestrictionType): AppScreen, Parcelable {

    @IgnoredOnParcel
    override val testTag: String = "app_restricted_screen"

    @Composable
    override fun ScreenContent() {
        val homeViewModel = getActivityScopedViewModel<HomeViewModel>()
        val navigator = LocalCodeNavigator.current
        val coroutineScope = rememberCoroutineScope()
        ContentRestrictedView(restrictionType) {
            coroutineScope.launch {
                homeViewModel.logout()
                    .onSuccess {
                        navigator.replaceAll(
                            ScreenRegistry.get(
                                AppRoute.Onboarding.Login()
                            )
                        )
                    }
            }
        }
    }
}