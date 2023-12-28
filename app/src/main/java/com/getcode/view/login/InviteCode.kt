package com.getcode.view.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.theme.BrandLight
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Preview
@Composable
fun InviteCode(
    viewModel: InviteCodeViewModel = hiltViewModel(),
    arguments: LoginArgs = LoginArgs()
) {
    val navigator = LocalCodeNavigator.current
    val dataState: InviteCodeUiModel by viewModel.uiFlow.collectAsState()
    val focusRequester = FocusRequester()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp, bottom = 20.dp)
            .padding(horizontal = 20.dp)
            .imePadding()
    ) {
        val (inviteCodeRow, captionText, buttonAction) = createRefs()

        TextField(
            modifier = Modifier
                .constrainAs(inviteCodeRow) {
                    linkTo(
                        start = parent.start,
                        top = parent.top,
                        end = parent.end,
                        bottom = parent.bottom,
                        verticalBias = 0.4f
                    )
                }

                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = 2.dp)
                .height(60.dp)
                .border(width = 1.dp, color = BrandLight, shape = RoundedCornerShape(5.dp))
                .background(White05),
            value = dataState.inviteCode,
            textStyle = MaterialTheme.typography.subtitle1,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                textColor = Color.White
            ),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { viewModel.onSubmit(navigator) }
            ),
            onValueChange = {
                if (!dataState.isLoading) {
                    viewModel.setInviteCodeInput(it)
                }
            },
            enabled = !dataState.isLoading && !dataState.isSuccess,
            placeholder = {
                Text(
                    text = stringResource(id = R.string.subtitle_inviteCode),
                    color = White50,
                    fontSize = 20.sp
                )
            }
        )

        Text(
            modifier = Modifier.constrainAs(captionText) {
                linkTo(inviteCodeRow.bottom, captionText.top, topMargin = 20.dp)
            },
            style = MaterialTheme.typography.body2.copy(
                textAlign = TextAlign.Center
            ),
            color = BrandLight,
            text = stringResource(id = R.string.subtitle_inviteCodeDescription)
        )

        CodeButton(
            modifier = Modifier
                .constrainAs(buttonAction) {
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                viewModel.onSubmit(navigator)
            },
            enabled = dataState.isContinue,
            isLoading = dataState.isLoading,
            isSuccess = dataState.isSuccess,
            text = stringResource(id = R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }

    SideEffect {
        focusRequester.requestFocus()
    }

    LaunchedEffect(rememberUpdatedState(Unit)) {
        viewModel.reset()
        arguments.phoneNumber?.let {
            viewModel.setPhoneNumber(it)
        }
    }
}

