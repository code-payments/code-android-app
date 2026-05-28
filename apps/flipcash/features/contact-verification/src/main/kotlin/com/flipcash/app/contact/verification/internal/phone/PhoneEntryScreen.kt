package com.flipcash.app.contact.verification.internal.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.phone.components.PhoneInputField
import com.flipcash.features.contact.verification.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.OnWindowFocusedRequester
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun PhoneEntryScreen(viewModel: PhoneVerificationViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    PhoneEntryScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun PhoneEntryScreenContent(
    state: PhoneVerificationViewModel.State,
    dispatchEvent: (PhoneVerificationViewModel.Event) -> Unit,
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
            ) { dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked) }
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
                PhoneInputField(
                    modifier = Modifier
                        .height(CodeTheme.dimens.grid.x12)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    focusManager = focusManager,
                    focusRequester = focusRequester,
                    locale = state.selectedLocale,
                    enabled = !state.sendingCode.loading && !state.sendingCode.success,
                    state = state.numberTextFieldState,
                    placeholder = stringResource(R.string.title_phoneNumber),
                    openCountrySelector = {
                        dispatchEvent(PhoneVerificationViewModel.Event.OpenCountrySelector)
                    },
                    onSubmit = { dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked) }
                )

                Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x2),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                    text = stringResource(R.string.subtitle_enterPhoneNumberToContinue)
                )
            }
        }

        OnWindowFocusedRequester(focusRequester)
    }
}