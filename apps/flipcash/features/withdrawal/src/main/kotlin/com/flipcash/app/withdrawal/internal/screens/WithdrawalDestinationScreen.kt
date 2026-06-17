package com.flipcash.app.withdrawal.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.app.withdrawal.internal.destination.WithdrawalDestinationScreen
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalDestinationScreen() {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val viewModel = flowSharedViewModel<WithdrawalViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdraw),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
        )
        WithdrawalDestinationScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnDestinationConfirmed>()
            .onEach { mint ->
                flowNavigator.navigateTo(WithdrawalStep.Confirmation)
            }.launchIn(this)
    }
}
