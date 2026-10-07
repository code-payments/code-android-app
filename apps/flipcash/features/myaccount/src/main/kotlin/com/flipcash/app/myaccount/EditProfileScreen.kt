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
import com.flipcash.app.myaccount.internal.editprofile.EditProfileScreenContent
import com.flipcash.app.myaccount.internal.editprofile.EditProfileViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun EditProfileScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<EditProfileViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = { AppBarDefaults.Title(text = stringResource(R.string.action_editProfile)) },
            titleAlignment = Alignment.CenterHorizontally,
            leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
        )
        EditProfileScreenContent(state = state, dispatch = viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditProfileViewModel.Event.OpenStep>()
            .onEach {
                navigator.push(
                    AppRoute.UpdateUserProfile(
                        origin = AppRoute.Menu.EditProfile,
                        steps = listOf(it.step),
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditProfileViewModel.Event.OpenScreen>()
            .onEach { navigator.push(it.screen) }
            .launchIn(this)
    }
}
