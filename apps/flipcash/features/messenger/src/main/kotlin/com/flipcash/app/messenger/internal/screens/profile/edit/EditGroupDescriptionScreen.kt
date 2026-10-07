package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.ui.transitions.RequestFocusWhenSettled
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
 * The group's description: a multiline field of three to eight lines, the characters left under it
 * (red once it is over), and a moderated description's refusal under that.
 */
@Composable
internal fun EditGroupDescriptionScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group

    val viewModel = hiltViewModel<EditGroupDescriptionViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val keyboard = rememberKeyboardController()

    // Keyed on the chat so the seed waits for the group and never runs over an edit in progress.
    LaunchedEffect(group?.chatId) {
        val resolved = group ?: return@LaunchedEffect
        viewModel.dispatchEvent(
            EditGroupDescriptionViewModel.Event.Initialize(
                chatId = resolved.chatId,
                description = resolved.description.orEmpty(),
            )
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditGroupDescriptionViewModel.Event.OnDescriptionAccepted>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }

    Column {
        AppBarWithTitle(
            title = stringResource(R.string.title_setGroupDescription),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { keyboard.hideIfVisible { flowNavigator.back() } },
        )
        val draft = state.draft
        CodeScaffold(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            bottomBar = {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            top = CodeTheme.dimens.grid.x6,
                            bottom = CodeTheme.dimens.staticGrid.x4,
                        ).imePadding(),
                    text = stringResource(R.string.action_save),
                    enabled = state.canSubmit,
                    isLoading = state.processingState.loading,
                    isSuccess = state.processingState.success,
                    onClick = {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(EditGroupDescriptionViewModel.Event.SaveClicked)
                        }
                    },
                )
            },
        ) { padding ->
            val focusRequester = remember { FocusRequester() }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(top = CodeTheme.dimens.staticGrid.x4),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
            ) {
                DisplayTextInput(
                    state = state.fieldState,
                    placeholder = stringResource(R.string.hint_groupDescription),
                    style = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.textMain),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                )

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = draft.remaining.toString(),
                    style = CodeTheme.typography.textSmall,
                    color = if (draft.remaining < 0) {
                        CodeTheme.colors.errorText
                    } else {
                        CodeTheme.colors.textSecondary
                    },
                    textAlign = TextAlign.End,
                )

                if (draft.moderated) {
                    Text(
                        text = stringResource(R.string.error_groupDescriptionNotAllowed),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.errorText,
                    )
                }
            }

            RequestFocusWhenSettled(focusRequester)
        }
    }
}
