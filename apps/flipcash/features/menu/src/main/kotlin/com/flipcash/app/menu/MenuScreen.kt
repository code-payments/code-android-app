package com.flipcash.app.menu

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.menu.internal.MenuScreenContent
import com.flipcash.app.menu.internal.MenuScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize

@Parcelize
class MenuScreen : ModalScreen, NamedScreen, Parcelable {
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
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home())) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MenuScreenViewModel.Event.OnLabsClicked>()
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.Lab)) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MenuScreenViewModel.Event.OnSwitchAccountTo>()
                .map { it.entropy }
                .onEach {
                    navigator.hide()
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home(it))) }
                .launchIn(this)
        }
    }
}