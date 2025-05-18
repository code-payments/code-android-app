package com.flipcash.app.withdrawal.internal.confirmation


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.money.formatted
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.features.withdrawal.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalConfirmationScreen(viewModel: WithdrawalViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    WithdrawalConfirmationScreenContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnWithdrawSuccessful>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.success_title_withdrawalComplete),
                        subtitle = resources.getString(R.string.success_description_withdrawalComplete),
                        showCancel = false,
                        showScrim = true,
                        type = BottomBarManager.BottomBarMessageType.SUCCESS,
                        actions = listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_ok),
                                onClick = {}
                            )
                        ),
                        onClose = {
                            navigator.popUntil { it == ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.Root) }
                        }
                    )
                )
            }.launchIn(this)
    }
}

@Composable
private fun WithdrawalConfirmationScreenContent(
    state: WithdrawalViewModel.State,
    dispatchEvent: (WithdrawalViewModel.Event) -> Unit
) {
    CodeScaffold(
        bottomBar = {
            CodeButton(
                modifier = Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                text = stringResource(R.string.action_withdraw),
                buttonState = ButtonState.Filled,
                isLoading = state.withdrawalState.loading,
                isSuccess = state.withdrawalState.success,
            ) {
                dispatchEvent(WithdrawalViewModel.Event.OnWithdraw)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset,),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset, Alignment.CenterVertically)
        ) {
            TransferInfo(
                amount = state.amountEntryState.selectedAmount,
                destination = state.destinationState.textFieldState.text.toString()
            )
        }
    }
}

@Composable
private fun TransferInfo(
    amount: LocalFiat,
    destination: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = CodeTheme.dimens.border,
                    color = CodeTheme.colors.brandLight,
                    shape = CodeTheme.shapes.medium
                )
                .background(Color(0xFF071F10), CodeTheme.shapes.medium)
                .padding(
                    horizontal = CodeTheme.dimens.grid.x4,
                    vertical = CodeTheme.dimens.grid.x12
                ),
            contentAlignment = Alignment.Center
        ) {
            val exchange = LocalExchange.current
            AmountArea(
                amountText = amount.formatted(),
                isAltCaption = false,
                isAltCaptionKinIcon = false,
                isClickable = false,
                currencyResId = exchange.getFlagByCurrency(amount.converted.currencyCode.name),
            )
        }

        Image(
            imageVector = Icons.Default.ArrowDownward,
            colorFilter = ColorFilter.tint(CodeTheme.colors.brandLight),
            contentDescription = ""
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = CodeTheme.dimens.border,
                    color = CodeTheme.colors.brandLight,
                    shape = CodeTheme.shapes.medium
                )
                .background(Color(0xFF071F10), CodeTheme.shapes.medium)
                .padding(CodeTheme.dimens.grid.x4),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = destination,
                style = CodeTheme.typography.textLarge.copy(textAlign = TextAlign.Center)
            )
        }
    }
}