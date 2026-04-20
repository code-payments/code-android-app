package com.flipcash.app.withdrawal.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.app.withdrawal.internal.entry.WithdrawalEntryScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalEntryContent(
    selectedMint: Mint,
) {
    val codeNavigator = LocalCodeNavigator.current
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val viewModel = flowSharedViewModel<WithdrawalViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdraw),
            isInModal = true,
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        WithdrawalEntryScreen(
            viewModel = viewModel,
            mint = selectedMint,
            onOpenRegionSelection = {
                codeNavigator.push(
                    AppRoute.Main.RegionSelection(kind = RegionSelectionKind.Entry)
                )
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnAmountAccepted>()
            .onEach {
                flowNavigator.navigateTo(WithdrawalStep.Destination)
            }.launchIn(this)
    }
}
