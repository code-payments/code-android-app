package com.flipcash.app.userprofile.internal.username

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Node 9491:6297 — the public `@handle`, a step in the profile update flow. Serves both entry
 * points and both cases: the "You" tab's progress card and My Account's Change Username row land
 * here, and a first claim differs from a change only in whether the field arrives prefilled.
 *
 * Mirrors [com.flipcash.app.userprofile.internal.name.NameEntryScreen]; the difference is the
 * input, which is held to the server's charset as it is typed.
 */
@Composable
internal fun UsernameEntryScreen() {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<UsernameEntryViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val keyboard = rememberKeyboardController()

    Column {
        // Always backable, unlike the name step: this is a pushed route for both the first claim
        // and a later change, never a mandatory step someone has to complete to get past it.
        AppBarWithTitle(
            onBackIconClicked = {
                keyboard.hideIfVisible {
                    // Leaving throws the edit away rather than carrying it back in. A gesture back
                    // lands in the same place by a different route: the step's ViewModel is scoped
                    // to its nav entry, so popping the entry drops the field with it.
                    viewModel.dispatchEvent(UsernameEntryViewModel.Event.DiscardChanges)
                    flowNavigator.back()
                }
            },
        )
        UsernameEntryScreenContent(state, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<UsernameEntryViewModel.Event.OnUsernameApproved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun UsernameEntryScreenContent(
    state: UsernameEntryViewModel.State,
    dispatchEvent: (UsernameEntryViewModel.Event) -> Unit,
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
                text = stringResource(R.string.title_usernameSelection),
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
                enabled = state.hasUsername && state.isChanged && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    keyboard.hideIfVisible {
                        dispatchEvent(UsernameEntryViewModel.Event.ConfirmUsernameChange)
                    }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        // Padding on the wrapper rather than the field — see NameEntryScreen: on the field it
        // inflates the box and drops the sublabel away from the entered text.
        Column(modifier = Modifier.padding(padding)) {
            DisplayTextInput(
                state = state.usernameFieldState,
                placeholder = stringResource(R.string.hint_username),
                sublabel = stringResource(R.string.subtitle_usernameSelection),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    // A handle is lowercase and unspaced; autocapitalizing it would only produce
                    // input the transformation below has to undo.
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = {
                    keyboard.hideIfVisible {
                        dispatchEvent(UsernameEntryViewModel.Event.ConfirmUsernameChange)
                    }
                },
                inputTransformation = UsernameInputTransformation,
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * Holds the field to the server's `^[a-z0-9_]+$` charset: uppercase is folded down and anything
 * else is dropped, so a paste out of another app lands as a usable handle instead of a rejection.
 *
 * Length is deliberately not clamped here — the design has "Too Short" and "Too Long" dialogs, so
 * over-typing has to be possible for the user to be told about it.
 */
private val UsernameInputTransformation = InputTransformation {
    val current = asCharSequence().toString()
    val sanitized = current.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
    if (sanitized != current) {
        replace(0, length, sanitized)
    }
}
