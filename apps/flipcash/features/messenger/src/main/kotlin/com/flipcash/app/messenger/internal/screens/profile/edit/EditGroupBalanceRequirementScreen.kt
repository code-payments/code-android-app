package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.profile.GroupBalanceRequirements
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * One of the group's minimum balances on the shared amount keypad, opened from a row of Edit
 * Group's Balance Requirements card. [role] picks the rule: Join is the listener rule, Chat the
 * speaker rule, falling back to Join's when the group has no speaker rule of its own — the same
 * reading the card shows.
 */
@Composable
internal fun EditGroupBalanceRequirementScreen(
    chatViewModel: ChatViewModel,
    role: GroupBalanceRole,
) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group

    val viewModel = hiltViewModel<EditGroupBalanceRequirementViewModel>()

    // Keyed on the chat: the seed waits for the group to resolve and must not run again, or a rule
    // arriving from the stream mid-edit would reset the baseline under the user.
    LaunchedEffect(group?.chatId) {
        val resolved = group ?: return@LaunchedEffect
        val requirements = GroupBalanceRequirements.from(resolved.rules)
        viewModel.dispatchEvent(
            EditGroupBalanceRequirementViewModel.Event.Initialize(
                chatId = resolved.chatId,
                role = role,
                current = when (role) {
                    GroupBalanceRole.Join -> requirements?.join
                    GroupBalanceRole.Chat -> requirements?.chat
                },
            )
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditGroupBalanceRequirementViewModel.Event.OnRequirementAccepted>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = {
            viewModel.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        },
        largeHeader = true,
        appBar = {
            AppBarWithTitle(
                title = stringResource(
                    when (role) {
                        GroupBalanceRole.Join -> R.string.title_groupJoinRequirement
                        GroupBalanceRole.Chat -> R.string.title_groupChatRequirement
                    }
                ),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
        headerCaption = {
            Text(
                text = stringResource(
                    when (role) {
                        GroupBalanceRole.Join -> R.string.description_groupJoinRequirement
                        GroupBalanceRole.Chat -> R.string.description_groupChatRequirement
                    }
                ),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Start,
            )
        },
    )
}
