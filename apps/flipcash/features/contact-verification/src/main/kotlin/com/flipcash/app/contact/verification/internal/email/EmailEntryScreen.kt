package com.flipcash.app.contact.verification.internal.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.features.contact.verification.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.TextInput
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun EmailEntryScreen(
    viewModel: EmailVerificationViewModel
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    EmailEntryScreenContent(
        state = state,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun EmailEntryScreenContent(
    state: EmailVerificationViewModel.State,
    dispatchEvent: (EmailVerificationViewModel.Event) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

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
                enabled = state.canSendCode && state.sendingCode.isIdle,
                isLoading = state.sendingCode.loading,
                isSuccess = state.sendingCode.success,
            ) { dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked) }
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
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                EmailAddressInputField(
                    state = state.email,
                    modifier = Modifier
                        .height(CodeTheme.dimens.grid.x12)
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .onPlaced {
                            focusRequester.requestFocus()
                        },
                    enabled = !state.sendingCode.loading && !state.sendingCode.success,
                    focusRequester = focusRequester,
                    onSubmit = { dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked) }
                )

                Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x2),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                    text = stringResource(R.string.subtitle_enterEmailToContinue)
                )
            }
        }
    }
}

@Composable
private fun EmailAddressInputField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSubmit: () -> Unit = {},
) {
    TextInput(
        modifier = modifier.focusRequester(focusRequester),
        state = state,
        enabled = enabled,
        placeholder = stringResource(R.string.title_email),
        placeholderStyle = CodeTheme.typography.textMedium,
        maxLines = 1,
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.grid.x3,
        ),
        colors = inputColors(placeholderColor = CodeTheme.colors.textSecondary,),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        onKeyboardAction = { onSubmit() },
    )
}