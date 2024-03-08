package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.WithdrawalArgs
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.green
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.TextInput

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountWithdrawAddress(
    viewModel: AccountWithdrawAddressViewModel,
    arguments: WithdrawalArgs,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = CodeTheme.dimens.inset)
            .imePadding()
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = stringResource(R.string.subtitle_whereToWithdrawKin),
            style = CodeTheme.typography.body1.copy(textAlign = TextAlign.Center),
            color = BrandLight
        )

        TextInput(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CodeTheme.dimens.grid.x4),
            state = dataState.addressText,
            maxLines = 1,
            placeholder = stringResource(R.string.subtitle_enterDestinationAddress),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dataState.isValid?.let { isValid ->
                Image(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                    painter = painterResource(
                        if (isValid) R.drawable.ic_checked_green else R.drawable.ic_xmark_red
                    ),
                    contentDescription = ""
                )

                val text =
                    if (isValid) {
                        if (dataState.hasResolvedDestination) {
                            stringResource(id = R.string.subtitle_validOwnerAccount)
                        } else stringResource(id = R.string.subtitle_validTokenAccount)
                    } else {
                        stringResource(id = R.string.subtitle_invalidTokenAccount)
                    }

                Text(
                    modifier = Modifier
                        .padding(start = CodeTheme.dimens.grid.x2),
                    text = text,
                    color = if (isValid) green else Color.Red,
                    style = CodeTheme.typography.caption
                )
            }
        }

        CodeButton(
            modifier = Modifier.fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x2),
            onClick = { viewModel.pasteAddress() },
            enabled = dataState.isPasteEnabled,
            text = stringResource(R.string.action_pasteFromClipboard),
            buttonState = ButtonState.Filled,
        )

        Spacer(modifier = Modifier.weight(1f))
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset),
            onClick = {
                viewModel.onSubmit(navigator, arguments)
            },
            enabled = dataState.isNextEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }

    SideEffect {
        viewModel.refreshPasteButtonState()
    }
}