package com.flipcash.app.myaccount

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.myaccount.internal.settings.SettingsScreen
import com.flipcash.app.myaccount.internal.settings.SettingsViewModel
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TopScrollEdge
import com.getcode.ui.components.toast.LocalFloatingToastHost
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val navigator = LocalCodeNavigator.current
    val billPlayground = LocalBillPlaygroundController.current
    val toasts = LocalFloatingToastHost.current
    val appUpdater = LocalAppUpdater.current

    val viewModel = hiltViewModel<SettingsViewModel>()

    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()

    // The list runs under the app bar and blurs into it (TopScrollEdge), as iOS's soft scroll edge
    // does, rather than stopping at the bar's bottom edge.
    CodeScaffold(
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            AppBarWithTitle(
                title = {
                    AppBarDefaults.Title(
                        text = stringResource(R.string.title_settings),
                    )
                },
                titleAlignment = Alignment.CenterHorizontally,
                leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
            )
        },
    ) { barPadding ->
        val topPadding = barPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                viewModel = viewModel,
                listState = listState,
                topPadding = topPadding,
                modifier = Modifier.hazeSource(hazeState),
            )
            TopScrollEdge(hazeState = hazeState, listState = listState, height = topPadding)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.CheckForUpdate>()
            .onEach { appUpdater.checkForUpdate() }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.OpenScreen>()
            .onEach { navigator.push(it.screen) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.OpenBillPlayground>()
            .onEach {
                navigator.hide()
                billPlayground.dispatchEvent(Event.Load())
            }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.OnViewAccessKey>()
            .onEach { navigator.push(AppRoute.Menu.BackupKey) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.OnLoggedOutCompletely>()
            .onEach {
                navigator.hide()
                navigator.replaceAll(AppRoute.OnboardingFlow())
            }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.OnAccountDeleted>()
            .onEach {
                navigator.hide()
                navigator.replaceAll(AppRoute.OnboardingFlow())
            }
            .launchIn(this)
    }

    // Hugs the message and passes taps through, so each tap still reaches the version row; each
    // update swaps the text in place and restarts the timeout rather than replaying the entrance.
    LaunchedEffect(viewModel, toasts) {
        if (toasts == null) return@LaunchedEffect
        viewModel.eventFlow
            .filterIsInstance<SettingsViewModel.Event.ShowDevModeToast>()
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
