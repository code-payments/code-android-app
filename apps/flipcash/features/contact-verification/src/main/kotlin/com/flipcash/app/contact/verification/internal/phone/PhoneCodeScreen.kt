package com.flipcash.app.contact.verification.internal.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.phone.components.OtpInputField
import com.flipcash.features.contact.verification.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.utils.replaceParam

@Composable
internal fun PhoneCodeScreen(
    viewModel: PhoneVerificationViewModel
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    PhoneCodeScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun PhoneCodeScreenContent(
    state: PhoneVerificationViewModel.State,
    dispatchEvent: (PhoneVerificationViewModel.Event) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        bottomBar = {
            CodeButton(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .imePadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_next),
                enabled = state.canConfirmCode && state.verifyingCode.isIdle,
                isLoading = state.verifyingCode.loading,
                isSuccess = state.verifyingCode.success,
            ) { dispatchEvent(PhoneVerificationViewModel.Event.OnVerifyCodeClicked) }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
            ) {
                OtpInputField(
                    state = state.codeTextFieldState,
                    lengthNeeded = state.otpLength,
                    focusRequester = focusRequester,
                )

                Text(
                    text = stringResource(R.string.subtitle_enterPhoneVerificationCode),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                )

                if (state.isResendTimerRunning) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.subtitle_didntGetCode)
                            .replaceParam(state.formattedPhone) +
                                "\n" +
                                stringResource(R.string.subtitle_requestNewOneIn)
                                    .replaceParam(
                                        "0:${if (state.resetTimeRemaining < 10) "0" else ""}${state.resetTimeRemaining}"
                                    ),
                        style = CodeTheme.typography.textSmall,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary,
                    )
                } else if (state.sendingCode.loading) {
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        modifier = Modifier
                            .size(CodeTheme.dimens.grid.x4),
                    )
                } else {
                    val text = buildAnnotatedString {
                        append(stringResource(R.string.subtitle_didntGetCode))
                        append(" ")
                        pushLink(
                            LinkAnnotation.Clickable(
                                tag = "resend",
                                styles = TextLinkStyles(
                                    SpanStyle(textDecoration = TextDecoration.Underline)
                                ),
                                linkInteractionListener = {
                                    dispatchEvent(PhoneVerificationViewModel.Event.OnCodeResendClicked)
                                }
                            ),
                        )
                        append(stringResource(R.string.subtitle_resend))
                        pop()
                    }
                    Text(
                        text = text,
                        style = CodeTheme.typography.textSmall,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}