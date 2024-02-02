package com.getcode.view.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalPhoneFormatter
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.network.repository.replaceParam
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = FocusRequester()

    fun cleanInputString(str: String): String =
        (if (str.length <= OTP_LENGTH) str else str.substring(
            0,
            OTP_LENGTH
        )).filter { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x4)
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        modifier = Modifier
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
                            .padding(top = CodeTheme.dimens.grid.x1),
                        length = OTP_LENGTH,
                        values = dataState.otpInput.orEmpty().toCharArray(),
                        onClick = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )
                }

                Column(
                    Modifier
                        .padding(top = CodeTheme.dimens.inset)
                        .wrapContentHeight()
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {

                    ProvideTextStyle(
                        CodeTheme.typography.body2.copy(
                            textAlign = TextAlign.Center,
                            color = BrandLight
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.subtitle_smsWasSent)
                        )
                        if (dataState.isResendTimerRunning) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.subtitle_didntGetCode)
                                    .replaceParam(
                                        LocalPhoneFormatter.current?.formatNumber(
                                            dataState.phoneNumberFormatted.orEmpty()
                                        )
                                    ) +
                                        "\n" +
                                        stringResource(R.string.subtitle_requestNewOneIn)
                                            .replaceParam(
                                                "0:${if (dataState.resetTimerTime < 10) "0" else ""}${dataState.resetTimerTime}"
                                            )
                            )
                        } else {
                            val text = buildAnnotatedString {
                                append(stringResource(id = R.string.subtitle_didntGetCodeResend))
                                append(" ")
                                pushStringAnnotation(
                                    tag = "resend",
                                    annotation = "resend code trigger"
                                )
                                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                                    append(stringResource(R.string.subtitle_resend))
                                }
                                pop()
                            }

                            ClickableText(
                                modifier = Modifier.fillMaxWidth(),
                                text = text,
                                style = LocalTextStyle.current
                            ) { offset ->
                                text.getStringAnnotations(
                                    tag = "resend",
                                    start = offset,
                                    end = offset
                                )
                                    .firstOrNull()?.let {
                                        viewModel.resendCode()
                                    }
                            }
                        }
                    }
                }
            }
        }

        CodeButton(
            modifier = Modifier.fillMaxWidth(),
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