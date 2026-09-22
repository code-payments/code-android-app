package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
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
 * The group's title, behind node 10187:110373's Name row.
 *
 * Takes [chatViewModel] for the chat it is editing and the title it starts from — the same reason
 * `GroupProfileScreen` does, and the reason this step carries no arguments — and keeps the edit
 * itself in its own nav-entry-scoped view model, so backing out drops the draft with the entry.
 */
@Composable
internal fun EditGroupNameScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group

    val viewModel = hiltViewModel<EditGroupNameViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val keyboard = rememberKeyboardController()

    // Keyed on the chat rather than Unit: the seed has to wait for the group to resolve, and must
    // not run again afterwards or it would overwrite the edit in progress.
    LaunchedEffect(group?.chatId) {
        val resolved = group ?: return@LaunchedEffect
        viewModel.dispatchEvent(
            EditGroupNameViewModel.Event.Initialize(
                chatId = resolved.chatId,
                title = resolved.groupTitle.orEmpty(),
            )
        )
    }

    // Only OK gets here. A refusal — a moderated title above all — keeps the user on the screen
    // with what they typed, which is the only state an amendment can start from.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditGroupNameViewModel.Event.OnTitleAccepted>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }

    // Laid out as NameEntryScreen lays out the same edit: the bar carries the back control and no
    // title, and the heading sits inside the scaffold above the field, so it scrolls and insets
    // with the content rather than floating in a bar over it.
    Column {
        AppBarWithTitle(
            onBackIconClicked = { keyboard.hideIfVisible { flowNavigator.back() } },
        )
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            topBar = {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(
                            top = CodeTheme.dimens.grid.x2,
                            bottom = CodeTheme.dimens.inset,
                        ),
                    text = stringResource(R.string.title_setGroupName),
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
                    text = stringResource(R.string.action_save),
                    enabled = state.canSubmit,
                    isLoading = state.processingState.loading,
                    isSuccess = state.processingState.success,
                    onClick = {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
                        }
                    },
                )
            },
        ) { padding ->
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.padding(padding)) {
                DisplayTextInput(
                    state = state.titleFieldState,
                    placeholder = stringResource(R.string.hint_groupName),
                    sublabel = stringResource(R.string.subtitle_groupNameLength),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked)
                        }
                    },
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}
