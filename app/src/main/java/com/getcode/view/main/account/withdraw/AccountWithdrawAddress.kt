package com.getcode.view.main.account.withdraw

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.theme.*
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun AccountWithdrawAddress(navController: NavController, arguments: Bundle?) {
    val viewModel = hiltViewModel<AccountWithdrawAddressViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp)
            .imePadding()
    ) {
        val (topText, addressField, resolveStatus, pasteButton, nextButton) = createRefs()
        Text(
            modifier = Modifier.constrainAs(topText) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            text = stringResource(R.string.subtitle_whereToWithdrawKin),
            style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
            color = BrandLight
        )

        OutlinedTextField(
            modifier = Modifier
                .constrainAs(addressField) {
                    top.linkTo(topText.bottom)
                }
                .padding(top = 20.dp)
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.subtitle_enterDestinationAddress),
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontSize = 16.sp,
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = VisualTransformation.None,
            value = dataState.addressText,
            onValueChange = { viewModel.setAddress(it) },
            textStyle = MaterialTheme.typography.subtitle1.copy(
                fontSize = 16.sp,
            ),
            singleLine = true,
            colors = inputColors(),
            shape = RoundedCornerShape(size = 5.dp)
        )

        Row(
            modifier = Modifier
                .constrainAs(resolveStatus) {
                    top.linkTo(addressField.bottom)
                }
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        ) {
            dataState.isValid?.let { isValid ->
                Image(
                    modifier = Modifier.size(20.dp),
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
                        .padding(start = 7.dp, top = 3.dp),
                    text = text,
                    color = if (isValid) green else Color.Red,
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 12.sp
                    )
                )
            }
        }

        CodeButton(
            modifier = Modifier
                .padding(bottom = 20.dp)
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
                .padding(bottom = 20.dp)
                .constrainAs(nextButton) {
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                arguments ?: return@CodeButton
                viewModel.onSubmit(navController, arguments)
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