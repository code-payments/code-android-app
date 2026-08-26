package com.flipcash.app.cash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.cash.internal.CashScreenViewModel
import com.flipcash.app.cash.internal.GiveScreenContent
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.session.LocalSessionController
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun CashScreen(
    selectedMint: Mint?,
    fromTokenInfo: Boolean,
) {
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.current!!

    val viewModel = hiltViewModel<CashScreenViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CashScreenViewModel.Event.PresentBill>()
            .onEach {
                session.showBill(it.bill)
                // This screen is reached as a PUSH (from currency-info), not a sheet, so hide() —
                // which only pops when a Sheet is on the stack — would leave it up. Pop back to the
                // currency-info underneath so the bill presents over it. The pop is deliberately
                // untransitioned (see AppContent's popTransitionSpec): the bill overlay + scrim are
                // drawn per nav entry, so an animated pop slides the outgoing entry's copy away while
                // the incoming entry composes its own — which reads as a flash behind the bill.
                navigator.pop()
            }
            .launchIn(this)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            // The pill is meant to sit centred in the bar. Say so explicitly rather than relying on
            // the leading slot's width to nudge a Start-aligned title into place — an empty leading
            // slot no longer reserves a phantom control's width, so a Start title sits flush at the
            // inset and the pill drifted left of centre whenever there was no back arrow.
            titleAlignment = Alignment.CenterHorizontally,
            title = {
                TokenSelectionPill(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    token = state.token?.token
                ) {
                    navigator.push(
                        AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                    )
                }
            },
            leftIcon = {
                if (fromTokenInfo) {
                    AppBarDefaults.UpNavigation { navigator.pop() }
                }
            },
            rightContents = {
                if (!fromTokenInfo) {
                    AppBarDefaults.Close { navigator.hide() }
                }
            }
        )
        GiveScreenContent(viewModel)
    }

    LaunchedEffect(viewModel, selectedMint) {
        viewModel.dispatchEvent(CashScreenViewModel.Event.InitializeToken(selectedMint))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CashScreenViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { navigator.push(it) }
            .launchIn(this)
    }
}
