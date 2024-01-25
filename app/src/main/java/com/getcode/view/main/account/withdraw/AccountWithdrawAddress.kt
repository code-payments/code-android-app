package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.WithdrawalArgs
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.theme.green
import com.getcode.theme.inputColors
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun AccountWithdrawAddress(
    viewModel: AccountWithdrawAddressViewModel,
    arguments: WithdrawalArgs,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = CodeTheme.dimens.inset)
            .imePadding()
    ) {
        val (topText, addressField, resolveStatus, pasteButton, nextButton) = createRefs()
        Text(
            modifier = Modifier.constrainAs(topText) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            text = stringResource(R.string.subtitle_whereToWithdrawKin),
            style = CodeTheme.typography.body1.copy(textAlign = TextAlign.Center),
            color = BrandLight
        )

        OutlinedTextField(
            modifier = Modifier
                .constrainAs(addressField) {
                    top.linkTo(topText.bottom)
                }
                .padding(top = CodeTheme.dimens.grid.x4)
                .fillMaxWidth()
                .padding(vertical = CodeTheme.dimens.grid.x1),
            placeholder = {
                Text(
                    text = stringResource(R.string.subtitle_enterDestinationAddress),
                    style = CodeTheme.typography.subtitle1.copy(
                        fontSize = 16.sp,
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = VisualTransformation.None,
            value = dataState.addressText,
            onValueChange = { viewModel.setAddress(it) },
            textStyle = CodeTheme.typography.subtitle1.copy(
                fontSize = 16.sp,
            ),
            singleLine = true,
            colors = inputColors(),
            shape = CodeTheme.shapes.extraSmall
        )

        Row(
            modifier = Modifier
                .constrainAs(resolveStatus) {
                    top.linkTo(addressField.bottom)
                }
                .fillMaxWidth()
                .padding(vertical = CodeTheme.dimens.grid.x1),
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
                            stringResource(id = R.string.withdraw_address_valid_owner_account)
                        } else stringResource(id = R.string.withdraw_address_valid_token_account)
                    } else {
                        stringResource(id = R.string.withdraw_address_invalid_destination)
                    }

                Text(
                    modifier = Modifier
                        .padding(start = CodeTheme.dimens.grid.x2),
                    text = text,
                    color = if (isValid) green else Color.Red,
                    style = CodeTheme.typography.caption.copy(
                        fontSize = 12.sp
                    )
                )
            }
        }

        CodeButton(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.inset)
                .constrainAs(pasteButton) {
                    top.linkTo(resolveStatus.bottom)
                },
            onClick = { viewModel.pasteAddress() },
            enabled = dataState.isPasteEnabled,
            text = stringResource(R.string.action_pasteFromClipboard),
            buttonState = ButtonState.Filled,
        )

        CodeButton(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.inset)
                .constrainAs(nextButton) {
                    bottom.linkTo(parent.bottom)
                },
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