package com.flipcash.app.myaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.myaccount.internal.UserProfileScreenContent
import com.flipcash.app.myaccount.internal.UserProfileViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun UserProfileScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<UserProfileViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = {
                AppBarDefaults.Title(
                    text = stringResource(R.string.title_userProfile),
                )
            },
            titleAlignment = Alignment.CenterHorizontally,
            leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
        )
        UserProfileScreenContent(state = state, dispatch = viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<UserProfileViewModel.Event.EditNameClicked>()
            .onEach {
                navigator.push(
                    AppRoute.UpdateUserProfile(
                        origin = AppRoute.Menu.UserProfile,
                        includeName = true,
                        includePhoto = false,
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<UserProfileViewModel.Event.EditPhotoClicked>()
            .onEach {
                navigator.push(
                    AppRoute.UpdateUserProfile(
                        origin = AppRoute.Menu.UserProfile,
                        includeName = false,
                        includePhoto = true,
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<UserProfileViewModel.Event.NavigateToPhoneVerification>()
            .onEach {
                navigator.push(
                    AppRoute.Verification(
                        origin = AppRoute.Menu.UserProfile,
                        includePhone = true,
                        includeEmail = false,
                        linkForPayment = state.phoneLinkedForPayment,
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<UserProfileViewModel.Event.NavigateToEmailVerification>()
            .onEach {
                navigator.push(
                    AppRoute.Verification(
                        origin = AppRoute.Menu.UserProfile,
                        includePhone = false,
                        includeEmail = true,
                    )
                )
            }.launchIn(this)
    }
}
