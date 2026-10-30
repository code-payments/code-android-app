package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.getcode.navigation.flow.rememberFlowNavigator
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

    NameEditScaffold(
        heading = stringResource(R.string.title_setGroupName),
        placeholder = stringResource(R.string.hint_groupNameEdit),
        field = state.titleFieldState,
        buttonText = stringResource(R.string.action_save),
        canSubmit = state.canSubmit,
        remaining = state.remaining,
        capitalization = KeyboardCapitalization.Words,
        isLoading = state.processingState.loading,
        isSuccess = state.processingState.success,
        onBack = { flowNavigator.back() },
        onSubmit = { viewModel.dispatchEvent(EditGroupNameViewModel.Event.SaveClicked) },
    )
}
