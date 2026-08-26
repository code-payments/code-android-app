package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
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
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.tipping.internal.MaxHandleLength
import com.flipcash.app.tipping.internal.NewChatViewModel
import com.flipcash.features.tipping.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Node 9442:5825 — start a chat with someone by their public `@handle`.
 *
 * Reached from the "+" on the Chats list, and left by becoming the chat itself: the handle resolves
 * to a user id, which is enough to open the conversation whether or not it exists yet
 * ([com.flipcash.app.core.chat.ChatIdentifier.ByUser]). The chat replaces this screen rather than
 * stacking on it, so backing out of the chat lands on the Chats list — the entry field has done its
 * job by then, and re-showing it would put a screen between the chat and the list it belongs to.
 */
@Composable
fun NewChatScreen() {
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()

    val viewModel = hiltViewModel<NewChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    // fillMaxSize + weight, rather than letting the scaffold size itself: the scaffold fills what
    // it is given, so stacking it under the app bar in a wrap-height column makes the column taller
    // than the screen and clips the "Next" bar off the bottom.
    Column(modifier = Modifier.fillMaxSize()) {
        AppBarWithTitle(
            onBackIconClicked = { keyboard.hideIfVisible { navigator.pop() } },
        )
        NewChatScreenContent(state, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel, navigator) {
        viewModel.eventFlow
            .filterIsInstance<NewChatViewModel.Event.UserResolved>()
            .onEach { navigator.replace(AppRoute.Messaging.Chat(it.identifier)) }
            .launchIn(this)
    }
}

@Composable
private fun ColumnScope.NewChatScreenContent(
    state: NewChatViewModel.State,
    dispatchEvent: (NewChatViewModel.Event) -> Unit,
) {
    val keyboard = rememberKeyboardController()
    CodeScaffold(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = CodeTheme.dimens.inset),
        topBar = {
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(
                        top = CodeTheme.dimens.grid.x2,
                        bottom = CodeTheme.dimens.inset,
                    ),
                text = stringResource(R.string.title_newChat),
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
                        bottom = CodeTheme.dimens.grid.x3,
                    ).imePadding(),
                text = stringResource(R.string.action_next),
                enabled = state.hasUsername && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    keyboard.hideIfVisible {
                        dispatchEvent(NewChatViewModel.Event.LookupUsername)
                    }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        // Padding on the wrapper rather than the field — on the field it inflates the box and drops
        // the sublabel away from the entered text (see UsernameEntryScreen).
        Column(modifier = Modifier.padding(padding)) {
            DisplayTextInput(
                state = state.usernameFieldState,
                placeholder = stringResource(R.string.hint_username),
                sublabel = stringResource(R.string.subtitle_newChat),
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
                        dispatchEvent(NewChatViewModel.Event.LookupUsername)
                    }
                },
                inputTransformation = HandleInputTransformation,
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * Holds the field to the same `^[a-z0-9_]+$` charset the claim screen enforces, so a handle pasted
 * out of a bio or a message — `@fred_wilson`, `Fred_Wilson` — is looked up as the server stores it
 * instead of missing.
 *
 * Length is clamped to the server's 15, unlike the claim screen, which lets the user over-type so
 * it can tell them the handle is too long. Nobody is choosing a handle here, so a longer one is
 * only a typo or an over-eager paste — and left unclamped it fails request validation, which
 * surfaces as "Something Went Wrong" rather than anything the user can act on.
 */
private val HandleInputTransformation = InputTransformation {
    val current = asCharSequence().toString()
    val sanitized = current.lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        .take(MaxHandleLength)
    if (sanitized != current) {
        replace(0, length, sanitized)
    }
}
