package com.flipcash.app.contact.verification.internal.email

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.contact.verification.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.debugBounds
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.utils.replaceParam

@Composable
internal fun EmailMagicLinkScreen(viewModel: EmailVerificationViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    EmailMagicLinkScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun EmailMagicLinkScreenContent(
    state: EmailVerificationViewModel.State,
    dispatchEvent: (EmailVerificationViewModel.Event) -> Unit,
) {
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .imePadding(),
                buttonState = ButtonState.Filled,
                enabled = state.verifyingCode.isIdle,
                isLoading = state.verifyingCode.loading,
                isSuccess = state.verifyingCode.success,
                text = stringResource(R.string.action_openMail),
            ) { dispatchEvent(EmailVerificationViewModel.Event.OpenMailApp) }
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
            ) {
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.ic_new_email),
                    contentDescription = null
                )

                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x8),
                    text = stringResource(R.string.title_checkYourInbox),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )

                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x6),
                    text = stringResource(R.string.subtitle_linkSentToEmail),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary
                )
                Text(
                    text = state.email.text.toString(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain
                )

                // Spacer with equal weight to balance the layout around subtitle_linkSentToEmail
                Spacer(Modifier.weight(0.5f))

                if (state.isResendTimerRunning) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.subtitle_didntGetEmail),
                        style = CodeTheme.typography.textSmall,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.subtitle_requestNewOneIn)
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
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.subtitle_didntGetEmail),
                        style = CodeTheme.typography.textSmall,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary,
                    )

                    val text = buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Clickable(
                                tag = "resend",
                                styles = TextLinkStyles(
                                    SpanStyle(textDecoration = TextDecoration.Underline)
                                ),
                                linkInteractionListener = {
                                    dispatchEvent(EmailVerificationViewModel.Event.OnResendCodeClicked)
                                }
                            ),
                        )
                        append(stringResource(R.string.subtitle_resend))
                        pop()
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = text,
                        style = CodeTheme.typography.textSmall,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                // Final spacer to maintain balance
                Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Preview
@Composable
private fun Preview_MagicLinkScreen() {
    FlipcashDesignSystem {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background)
        ) {
            EmailMagicLinkScreenContent(
                state = EmailVerificationViewModel.State(
                    email = TextFieldState("james.k.polk@examplepetstore.com")
                ),
            ) {}
        }
    }
}