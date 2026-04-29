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
import com.flipcash.app.withdrawal.internal.confirmation.WithdrawalConfirmationScreen
import com.flipcash.core.R
import com.flipcash.features.withdrawal.R as WR
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalConfirmationScreen(mint: Mint) {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val viewModel = flowSharedViewModel<WithdrawalViewModel>()
    val resources = LocalResources.current

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdraw),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
        )
        WithdrawalConfirmationScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnUsdcWithdrawalProcessing>()
            .map { it.swapId }
            .onEach { swapId ->
                flowNavigator.navigateTo(WithdrawalStep.Processing(swapId))
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnWithdrawSuccessful>()
            .onEach {
                BottomBarManager.showSuccess(
                    title = resources.getString(WR.string.success_title_withdrawalComplete),
                    message = resources.getString(WR.string.success_description_withdrawalComplete),
                    showCancel = false,
                    showScrim = true,
                    onDismiss = {
                        flowNavigator.exitWithResult(WithdrawalResult.Success)
                    }
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnWithdrawalTooSmall>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(WR.string.error_title_withdrawalTooSmall),
                    message = resources.getString(WR.string.error_description_withdrawalTooSmall),
                    showCancel = false,
                    onDismiss = {
                        flowNavigator.replaceStack(listOf(WithdrawalStep.Amount(mint)))
                    }
                )
            }.launchIn(this)
    }
}
