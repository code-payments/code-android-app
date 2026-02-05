package com.flipcash.app.tokens.internal

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.core.ui.buildNotifyButtonLabel
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.BuySellSwapTokenViewModel
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.notificationPermissionCheck
import com.getcode.view.LoadingSuccessState

@Composable
internal fun TokenTxProcessingScreen(
    viewModel: BuySellSwapTokenViewModel
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenTxProcessingScreen(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun TokenTxProcessingScreen(
    state: BuySellSwapTokenViewModel.State,
    dispatch: (BuySellSwapTokenViewModel.Event) -> Unit,
) {
    val permissions = LocalPermissionChecker.current
    var hasPushPerms by remember {
        mutableStateOf(permissions.isGranted(Manifest.permission.POST_NOTIFICATIONS))
    }

    val notificationPermissionCheck = notificationPermissionCheck { hasPushPerms = it }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                isInModal = true,
                title = when (state.purpose) {
                    is TokenSwapPurpose.BalanceIncrease -> stringResource(
                        R.string.title_purchasingToken,
                        state.tokenName
                    )

                    is TokenSwapPurpose.BalanceDecrease -> stringResource(
                        R.string.title_sellingToken,
                        state.tokenName
                    )

                    else -> ""
                },
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
        bottomBar = {
            when (state.processingProgress.state) {
                LoadingSuccessState.State.Idle -> Unit
                LoadingSuccessState.State.Loading -> {
                    val notifyLabel = buildNotifyButtonLabel()
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        text = if (hasPushPerms) {
                            notifyLabel.first
                        } else {
                            AnnotatedString(stringResource(R.string.action_notifyMeWhenComplete))
                        },
                        inlineContent = if (hasPushPerms) {
                            notifyLabel.second
                        } else {
                            emptyMap()
                        },
                        buttonState = ButtonState.Filled,
                        enabled = !hasPushPerms,
                        onClick = {
                            notificationPermissionCheck(true)
                        }
                    )
                }
                LoadingSuccessState.State.Success,
                LoadingSuccessState.State.Error -> {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        text = stringResource(R.string.action_ok),
                        buttonState = ButtonState.Filled,
                        onClick = {
                            dispatch(BuySellSwapTokenViewModel.Event.Exit)
                        }
                    )
                }
            }

        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
            ) {
                Crossfade(state.processingProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.24f)
                            .aspectRatio(1f),
                    ) {
                        when (it.state) {
                            LoadingSuccessState.State.Error -> Image(
                                modifier = Modifier
                                    .matchParentSize(),
                                painter = painterResource(R.drawable.ic_circle_exclamation_large),
                                contentDescription = null,
                            )

                            LoadingSuccessState.State.Idle -> Unit
                            LoadingSuccessState.State.Loading -> CodeCircularProgressIndicator(
                                modifier = Modifier
                                    .matchParentSize(),
                                strokeWidth = CodeTheme.dimens.grid.x1,
                                color = Color.White,
                                backgroundColor = Color.White.copy(0.30f),
                                strokeCap = StrokeCap.Butt,
                            )

                            LoadingSuccessState.State.Success -> Image(
                                modifier = Modifier
                                    .matchParentSize(),
                                painter = painterResource(R.drawable.ic_circle_check_large),
                                contentDescription = null,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when (state.processingProgress.state) {
                            LoadingSuccessState.State.Error -> stringResource(R.string.error_title_buySellFailed)
                            LoadingSuccessState.State.Idle -> ""
                            LoadingSuccessState.State.Loading -> stringResource(R.string.title_processingYourTransaction)
                            LoadingSuccessState.State.Success -> {
                                val name = when (state.purpose) {
                                    is TokenSwapPurpose.BalanceIncrease -> state.tokenName
                                    else -> stringResource(R.string.title_cashReserves)
                                }
                                state.netTransferAmount.formatted(suffix = stringResource(R.string.label_ofToken, name))
                            }
                        },
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = when (state.processingProgress.state) {
                            LoadingSuccessState.State.Error -> stringResource(R.string.error_description_buySellFailed)
                            LoadingSuccessState.State.Idle -> ""
                            LoadingSuccessState.State.Loading -> stringResource(R.string.subtitle_processingYourTransaction)
                            LoadingSuccessState.State.Success -> stringResource(R.string.subtitle_wasAddedToYourWallet)
                        },
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun TxProcessiongPreview() {
    FlipcashPreview {
        TokenTxProcessingScreen(
            state = BuySellSwapTokenViewModel.State(
                processingProgress = LoadingSuccessState(loading = true)
            )
        ) { }
    }
}