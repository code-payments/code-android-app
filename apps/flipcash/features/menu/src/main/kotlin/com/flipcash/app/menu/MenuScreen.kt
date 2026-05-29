package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.internal.MenuScreenContent
import com.flipcash.app.menu.internal.MenuScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun MenuScreen() {
    val viewModel = hiltViewModel<MenuScreenViewModel>()
    MenuScreenContent(viewModel)

    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MenuScreenViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { navigator.push(it) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MenuScreenViewModel.Event.OnSwitchAccountTo>()
            .onEach { navigator.hide() }
            .launchIn(this)
    }
}
