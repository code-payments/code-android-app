package com.flipcash.app.withdrawal.internal.destination

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.features.withdrawal.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.TextInput
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalDestinationScreen(viewModel: WithdrawalViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    WithdrawalDestinationScreenContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnDestinationConfirmed>()
            .onEach {
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.Withdrawal.Confirmation))
            }.launchIn(this)
    }
}

@Composable
private fun WithdrawalDestinationScreenContent(
    state: WithdrawalViewModel.State,
    dispatchEvent: (WithdrawalViewModel.Event) -> Unit
) {
    val keyboard = rememberKeyboardController()

    CodeScaffold(
        bottomBar = {
            CodeButton(
                modifier = Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                enabled = state.destinationState.availability?.isValid == true,
                text = stringResource(R.string.action_next),
                buttonState = ButtonState.Filled,
            ) {
                dispatchEvent(WithdrawalViewModel.Event.OnDestinationConfirmed)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset,),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            Text(
                text = stringResource(
                    R.string.subtitle_whereWithdrawUsdcTo,
                    state.amountEntryState.formattedAmount
                ),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                TextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x2),
                    state = state.destinationState.textFieldState,
                    placeholder = stringResource(R.string.title_enterAddress),
                    placeholderStyle = CodeTheme.typography.textMedium,
                    maxLines = 1,
                    contentPadding = PaddingValues(CodeTheme.dimens.grid.x2),
                    colors = inputColors(placeholderColor = CodeTheme.colors.textSecondary,),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                AnimatedContent(
                    modifier = Modifier.fillMaxWidth(),
                    targetState = state.destinationState.availability,
                    label = "address validity",
                    transitionSpec = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Down) togetherWith
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)
                    }
                ) { availability ->
                    if (availability != null && availability.isValid) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                                painter = painterResource(R.drawable.ic_checked_green),
                                contentDescription = ""
                            )

                            Text(
                                text = if (availability.hasResolvedDestination) {
                                    stringResource(id = R.string.subtitle_validOwnerAccount)
                                } else {
                                    stringResource(id = R.string.subtitle_validTokenAccount)
                                },
                                color = CodeTheme.colors.success,
                                style = CodeTheme.typography.caption
                            )
                        }
                    } else {
                        Spacer(Modifier.fillMaxWidth())
                    }
                }
            }

            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_pasteFromClipboard),
                buttonState = ButtonState.Filled,
                isLoading = state.destinationState.checkingClipboard.loading,
                isSuccess = state.destinationState.checkingClipboard.success,
            ) {
                keyboard.hideIfVisible {
                    dispatchEvent(WithdrawalViewModel.Event.PasteFromClipboard)
                }
            }
        }
    }
}