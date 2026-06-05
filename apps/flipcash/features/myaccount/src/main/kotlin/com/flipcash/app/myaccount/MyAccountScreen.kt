package com.flipcash.app.myaccount


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.myaccount.internal.MyAccountScreen
import com.flipcash.app.myaccount.internal.MyAccountScreenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun MyAccountScreen() {
    val navigator = LocalCodeNavigator.current

    val viewModel = hiltViewModel<MyAccountScreenViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = {
                AppBarDefaults.Title(
                    text = stringResource(R.string.title_myAccount),
                )
            },
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
        )
        MyAccountScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MyAccountScreenViewModel.Event.OnLoggedOutCompletely>()
            .onEach {
                navigator.hide()
                navigator.replaceAll(AppRoute.OnboardingFlow()) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MyAccountScreenViewModel.Event.OnAccountDeleted>()
            .onEach {
                navigator.hide()
                navigator.replaceAll(AppRoute.OnboardingFlow()) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MyAccountScreenViewModel.Event.OnViewAccessKey>()
            .onEach {
                navigator.push(AppRoute.Menu.BackupKey) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MyAccountScreenViewModel.Event.OnViewUserProfile>()
            .onEach { navigator.push(AppRoute.Menu.UserProfile) }
            .launchIn(this)
    }
}
