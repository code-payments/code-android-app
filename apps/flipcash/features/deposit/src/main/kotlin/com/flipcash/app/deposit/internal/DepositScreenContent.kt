package com.flipcash.app.deposit.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.features.deposit.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.extraSmall
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun DepositScreen(viewModel: DepositViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    DepositScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun DepositScreenContent(state: DepositViewModel.State, dispatchEvent: (DepositViewModel.Event) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    CodeScaffold(
        topBar = {
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .fillMaxWidth(),
                text = stringResource(R.string.subtitle_howToDeposit),
                color = CodeTheme.colors.textSecondary,
                style = CodeTheme.typography.textSmall,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        dispatchEvent(DepositViewModel.Event.CopyAddress)
                    },
                    text = stringResource(if (state.isCopied) R.string.action_copied else R.string.action_copyAddress),
                    enabled = !state.isCopied,
                    isSuccess = state.isCopied,
                    buttonState = ButtonState.Filled,
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        uriHandler.openUri(
                            context.getString(R.string.external_url_deposit)
                        )
                    },
                    text = stringResource(R.string.action_learnHowToDepositFunds),
                    buttonState = ButtonState.Subtle,
                )
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(vertical = CodeTheme.dimens.grid.x3)
                .clip(CodeTheme.shapes.extraSmall)
                .border(
                    width = CodeTheme.dimens.border,
                    color = CodeTheme.colors.border,
                    shape = CodeTheme.shapes.extraSmall
                )
                .fillMaxWidth()
                .height(CodeTheme.dimens.grid.x10)
                .background(White05)
                .rememberedClickable { dispatchEvent(DepositViewModel.Event.CopyAddress) }
                .padding(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f),
                text = state.depositAddress,
                color = White,
                style = CodeTheme.typography.textMedium.copy(textAlign = TextAlign.Center),
                overflow = TextOverflow.MiddleEllipsis,
                maxLines = 1
            )
        }
    }
}