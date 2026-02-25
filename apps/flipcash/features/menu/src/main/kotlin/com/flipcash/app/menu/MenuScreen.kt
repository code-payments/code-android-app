package com.flipcash.app.menu

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.internal.MenuScreenContent
import com.flipcash.app.menu.internal.MenuScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class MenuScreen : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "menu_screen"

    @Composable
    override fun ModalContent() {
        val viewModel = getViewModel<MenuScreenViewModel>()
        MenuScreenContent(viewModel)

        val navigator = LocalCodeNavigator.current

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MenuScreenViewModel.Event.OnLoggedOutCompletely>()
                .onEach {
                    navigator.hide()
                    navigator.replaceAll(ScreenRegistry.get(AppRoute.Onboarding.Login())) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MenuScreenViewModel.Event.OpenScreen>()
                .map { ScreenRegistry.get(it.screen) }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MenuScreenViewModel.Event.OnSwitchAccountTo>()
                .map { it.entropy }
                .onEach {
                    navigator.hide()
                    navigator.replaceAll(ScreenRegistry.get(AppRoute.Onboarding.Login(it))) }
                .launchIn(this)
        }
    }
}