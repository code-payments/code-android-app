package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.menu.internal.MenuScreenContent
import com.flipcash.app.menu.internal.MenuScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.toast.LocalFloatingToastHost
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun MenuScreen() {
    val viewModel = hiltViewModel<MenuScreenViewModel>()
    MenuScreenContent(viewModel)

    val navigator = LocalCodeNavigator.current
    val toasts = LocalFloatingToastHost.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MenuScreenViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { navigator.push(it) }
            .launchIn(this)
    }

    // The menu is a tab home, so this rises out of the tab bar, over the version row the taps land
    // on. It hugs the message and passes taps through, so each tap still reaches the row; each
    // update swaps the text in place and restarts the timeout rather than replaying the entrance.
    LaunchedEffect(viewModel, toasts) {
        if (toasts == null) return@LaunchedEffect
        viewModel.eventFlow
            .filterIsInstance<MenuScreenViewModel.Event.ShowDevModeToast>()
            .onEach { event ->
                launch {
                    toasts.show(
                        message = event.message,
                        hugContent = true,
                        passThrough = true,
                        inPlace = true,
                    )
                }
            }
            .launchIn(this)
    }
}
