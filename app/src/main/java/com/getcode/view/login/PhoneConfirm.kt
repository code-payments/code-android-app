package com.getcode.view.login

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.network.repository.replaceParam
import com.getcode.theme.BrandLight
import com.getcode.theme.topBarHeight
import com.getcode.util.PhoneUtils
import com.getcode.view.*
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.OtpRow

const val OTP_LENGTH = 6

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun PhoneConfirm(
    navController: NavController? = null,
    arguments: Bundle? = null
) {
    val viewModel = hiltViewModel<PhoneConfirmViewModel>()
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
            .imePadding()
            .padding(horizontal = 20.dp)
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
                .width(5.dp)
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
                .padding(top = 5.dp)
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
                    .padding(top = 20.dp)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .constrainAs(captionText) {
                        top.linkTo(otpRow.bottom)
                    }
                    .padding(vertical = 10.dp),
                color = BrandLight,
                style = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
                text = stringResource(R.string.subtitle_didntGetCode)
                    .replaceParam(PhoneUtils.formatNumber(dataState.phoneNumberFormatted.orEmpty())) +
                        " " +
                        stringResource(R.string.subtitle_requestNewOneIn)
                            .replaceParam(
                                "0:${if (dataState.resetTimerTime < 10) "0" else ""}${dataState.resetTimerTime}"
                            )
            )
        } else {
            CodeButton(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .constrainAs(captionText) {
                        top.linkTo(otpRow.bottom)
                    }
                    .padding(vertical = 10.dp),
                buttonState = ButtonState.Subtle,
                text = stringResource(R.string.subtitle_didntGetCodeResend)
                    .replaceParam(dataState.phoneNumber),
                onClick = { viewModel.resendCode() }
            )
        }


        CodeButton(
            modifier = Modifier
                .padding(bottom = 10.dp)
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

    LaunchedEffect(rememberUpdatedState(Unit)) {
        viewModel.reset(navController)
        val phoneNumber = arguments?.getString(ARG_PHONE_NUMBER).orEmpty()

        viewModel.setPhoneNumber(phoneNumber)

        arguments?.getString(ARG_SIGN_IN_ENTROPY_B64)
            ?.let { viewModel.setSignInEntropy(it) }
        arguments?.getBoolean(ARG_IS_PHONE_LINKING)
            ?.let { viewModel.setIsPhoneLinking(it) }
        arguments?.getBoolean(ARG_IS_NEW_ACCOUNT)
            ?.let { viewModel.setIsNewAccount(it) }
    }
}