package com.getcode.view.login

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalPhoneFormatter
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.network.repository.replaceParam
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.topBarHeight
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.OtpRow

const val OTP_LENGTH = 6

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun PhoneConfirm(
    viewModel: PhoneConfirmViewModel = hiltViewModel(),
    arguments: LoginArgs = LoginArgs(),
) {
    val navigator = LocalCodeNavigator.current

    val dataState by viewModel.uiFlow.collectAsState()
    val inputService = LocalTextInputService.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = FocusRequester()

    fun cleanInputString(str: String): String =
        (if (str.length <= OTP_LENGTH) str else str.substring(
            0,
            OTP_LENGTH
        )).filter { it.isDigit() }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = topBarHeight)
    ) {
        val (captionText, input, buttonAction, otpRow) = createRefs()

        TextField(
            modifier = Modifier
                .constrainAs(input) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .width(CodeTheme.dimens.grid.x1)
                .alpha(0f)
                .focusRequester(focusRequester),
            value = dataState.otpInputTextFieldValue,
            onValueChange = {
                viewModel.onOtpInputChange(cleanInputString(it.text))
            },
            readOnly = dataState.otpInputTextFieldValue.text.length == OTP_LENGTH,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        )

        OtpRow(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x1)
                .constrainAs(otpRow) {
                    top.linkTo(parent.top)
                    bottom.linkTo(buttonAction.top)
                },
            length = OTP_LENGTH,
            values = dataState.otpInput.orEmpty().toCharArray(),
            onClick = {
                focusRequester.requestFocus()
                inputService?.showSoftwareKeyboard()
            }
        )

        if (dataState.isResendTimerRunning) {
            Text(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.inset)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .constrainAs(captionText) {
                        top.linkTo(otpRow.bottom)
                    }
                    .padding(vertical = CodeTheme.dimens.grid.x2),
                color = BrandLight,
                style = CodeTheme.typography.body2.copy(textAlign = TextAlign.Center),
                text = stringResource(R.string.subtitle_didntGetCode)
                    .replaceParam(LocalPhoneFormatter.current?.formatNumber(dataState.phoneNumberFormatted.orEmpty())) +
                        " " +
                        stringResource(R.string.subtitle_requestNewOneIn)
                            .replaceParam(
                                "0:${if (dataState.resetTimerTime < 10) "0" else ""}${dataState.resetTimerTime}"
                            )
            )
        } else {
            CodeButton(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.inset)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .constrainAs(captionText) {
                        top.linkTo(otpRow.bottom)
                    }
                    .padding(vertical = CodeTheme.dimens.grid.x2),
                buttonState = ButtonState.Subtle,
                text = stringResource(R.string.subtitle_didntGetCodeResend)
                    .replaceParam(dataState.phoneNumber),
                onClick = { viewModel.resendCode() }
            )
        }


        CodeButton(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.grid.x2)
                .constrainAs(buttonAction) {
                    //linkTo(buttonAction.bottom, parent.bottom, bias = 1.0F)
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                viewModel.onSubmit()
            },
            isLoading = dataState.isLoading,
            isSuccess = dataState.isSuccess,
            enabled = false,
            text = stringResource(R.string.action_confirm),
            buttonState = ButtonState.Filled,
        )
    }

    SideEffect {
        focusRequester.requestFocus()
    }

    LaunchedEffect(dataState.isSuccess) {
        if (dataState.isSuccess) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(arguments) {
        viewModel.reset(navigator)
        val phoneNumber = arguments.phoneNumber.orEmpty()

        viewModel.setPhoneNumber(phoneNumber)

        arguments.signInEntropy
            ?.let { viewModel.setSignInEntropy(it) }
        viewModel.setIsPhoneLinking(arguments.isPhoneLinking)
        viewModel.setIsNewAccount(arguments.isNewAccount)
    }
}