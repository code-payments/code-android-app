package com.flipcash.app.tokens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.onramp.ui.buildPhantomButtonLabel
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PhantomConnectConfirmationScreen() {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomCeremonyFailed>()
            .onEach { flowNavigator.exitCanceled() }
            .launchIn(this)
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomConnected>()
            .onEach { flowNavigator.navigateTo(SwapStep.PhantomConfirmTransaction) }
            .launchIn(this)
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_purchase),
                isInModal = true,
                backButton = true,
                onBackIconClicked = { flowNavigator.back() },
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_connectYourPhantomWallet),
                isLoading = state.buyProgress.loading,
                enabled = state.buyProgress.isIdle,
            ) {
                viewModel.dispatchEvent(SwapViewModel.Event.StartPhantomCeremony)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x11),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_phantom_buy_connect),
                        contentDescription = null,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.60f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.title_buyWithPhantom),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(R.string.description_buyWithPhantom),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PhantomTransactionConfirmationScreen() {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomNavigateToProcessing>()
            .onEach { event ->
                flowNavigator.navigateTo(SwapStep.Processing(event.swapId))
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomCeremonyFailed>()
            .onEach { flowNavigator.exitCanceled() }
            .launchIn(this)
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_purchase),
                isInModal = true,
                backButton = true,
                onBackIconClicked = { flowNavigator.back() },
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
        bottomBar = {
            val label = buildPhantomButtonLabel(
                prefix = stringResource(com.flipcash.features.tokens.R.string.label_confirmIn),
                isEnabled = state.canTransact
            )

            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                text = label.first,
                inlineContent = label.second,
                isLoading = state.buyProgress.loading,
                enabled = state.buyProgress.isIdle,
            ) {
                viewModel.dispatchEvent(SwapViewModel.Event.ConfirmPhantomTransaction)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x11),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_phantom_connected),
                        contentDescription = null,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.80f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.title_buyWithPhantomConnected),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(R.string.description_buyWithPhantomConnected),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
