package com.flipcash.app.userprofile.internal.name

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedElementTransition
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun NameEntryScreen() {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<NameEntryViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val keyboard = rememberKeyboardController()

    Column {
        AppBarWithTitle(
            backButton = true,
            onBackIconClicked = {
                keyboard.hideIfVisible {
                    flowNavigator.back()
                }
            },
        )
        NameEntryScreenContent(state, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<NameEntryViewModel.Event.OnNameApproved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun NameEntryScreenContent(
    state: NameEntryViewModel.State,
    dispatchEvent: (NameEntryViewModel.Event) -> Unit,
) {
    val keyboard = rememberKeyboardController()
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        topBar = {
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(
                        top = CodeTheme.dimens.grid.x2,
                        bottom = CodeTheme.dimens.inset
                    ),
                text = stringResource(R.string.title_profileNameSelection),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.grid.x3
                    ).imePadding(),
                text = stringResource(R.string.action_next),
                enabled = state.hasName && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    keyboard.hideIfVisible {
                        dispatchEvent(NameEntryViewModel.Event.CheckName)
                    }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        DisplayTextInput(
            state = state.nameFieldState,
            placeholder = stringResource(R.string.hint_profileName),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .focusRequester(focusRequester),
            textModifier = Modifier.sharedElementTransition(
                transition = SharedTransition.CurrencyName,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            onKeyboardAction = {
                keyboard.hideIfVisible {
                    dispatchEvent(NameEntryViewModel.Event.CheckName)
                }
            },
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}