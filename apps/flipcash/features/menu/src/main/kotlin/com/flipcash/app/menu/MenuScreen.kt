package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.menu.internal.MenuScreenContent
import com.flipcash.app.menu.internal.MenuScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * @param onClose leaves You, when it is drawn without the tab bar (the Group Money shell has no tab
 * for it); it draws a leading close. Null when You is a tab, as it is today.
 */
@Composable
fun MenuScreen(onClose: (() -> Unit)? = null) {
    val viewModel = hiltViewModel<MenuScreenViewModel>()
    MenuScreenContent(viewModel, onClose)

    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MenuScreenViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { navigator.push(it) }
            .launchIn(this)
    }
}
