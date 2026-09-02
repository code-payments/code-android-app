package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.core.measured
import com.getcode.ui.core.unboundedClickable

@Composable
internal fun ChatTopBar(
    navigator: CodeNavigator,
    state: ChatViewModel.State,
    chatActionHandler: ChatActionHandler,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    var titleHeight by remember { mutableStateOf(0.dp) }
    val bgColor = CodeTheme.colors.background
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight + 24.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to bgColor,
                            1f to Color.Transparent,
                        )
                    )
                )
        )
        // Selecting a message takes over the bar rather than stacking a second one over it, so the
        // conversation's own actions can't be reached while a message action is pending.
        AnimatedContent(
            modifier = Modifier.measured { titleHeight = it.height },
            targetState = state.selection,
            contentKey = { it != null },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "chat top bar",
        ) { selection ->
            if (selection == null) {
                ConversationTitleBar(navigator, state, chatActionHandler)
            } else {
                MessageSelectionBar(selection, dispatch)
            }
        }
    }
}

@Composable
private fun ConversationTitleBar(
    navigator: CodeNavigator,
    state: ChatViewModel.State,
    chatActionHandler: ChatActionHandler,
) {
    AppBarWithTitle(
        leftIcon = {
            AppBarDefaults.UpNavigation { navigator.pop() }
        },
        title = {
            Row(
                // Profile open is only available for tip DMs (see State.canViewProfile).
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.canViewProfile) {
                            Modifier.unboundedClickable {
                                chatActionHandler(ChatAction.ViewProfile)
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                ParticipantAvatar(
                    participant = state.participant,
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.staticGrid.x8)
                        .clip(CircleShape),
                )

                Text(
                    modifier = Modifier.weight(1f),
                    // Name-or-handle: the bar is one line (node 9443:9094), and the handle is
                    // the only identity a name-less tip DM counterparty has.
                    text = state.participant?.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    )
}

/**
 * The bar a long-press puts up, offering exactly what the transcript resolved for that message.
 *
 * Delete sits inline and the rest go under the overflow, following WhatsApp: the destructive action
 * is the one worth a dedicated target, and burying it would make the common case a two-tap one.
 */
@Composable
private fun MessageSelectionBar(
    selection: ChatListItem.ContentBubble,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    val capabilities = selection.capabilities
    val body = selection.plainText

    AppBarWithTitle(
        leftIcon = {
            CircularIconButton(
                onClick = { dispatch(ChatViewModel.Event.ClearMessageSelection) },
                testTag = "action_clear_message_selection",
            ) { size ->
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_clearMessageSelection),
                    tint = Color.White,
                    modifier = Modifier.requiredSize(size),
                )
            }
        },
        title = {
            // One message at a time today, but the count is what the bar shows and what a later
            // multi-selection would change, so it renders as a count rather than a fixed label.
            AppBarDefaults.Title(text = "1")
        },
        rightContents = {
            if (MessageCapability.Delete in capabilities) {
                CircularIconButton(
                    onClick = { dispatch(ChatViewModel.Event.DeleteMessage(selection.messageId)) },
                    testTag = "action_delete_message",
                ) { size ->
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                        modifier = Modifier.requiredSize(size),
                    )
                }
            }

            if (body != null) {
                MessageOverflow(
                    capabilities = capabilities,
                    onCopy = { dispatch(ChatViewModel.Event.CopyMessage(body)) },
                    onEdit = { dispatch(ChatViewModel.Event.EditMessage(selection.messageId, body)) },
                )
            }
        },
    )
}

@Composable
private fun MessageOverflow(
    capabilities: Set<MessageCapability>,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
    val canCopy = MessageCapability.Copy in capabilities
    val canEdit = MessageCapability.Edit in capabilities
    if (!canCopy && !canEdit) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        AppBarDefaults.Overflow(onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            containerColor = CodeTheme.colors.brandLight,
            onDismissRequest = { expanded = false },
        ) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { OverflowLabel(stringResource(R.string.action_copy)) },
                    onClick = {
                        expanded = false
                        onCopy()
                    },
                )
            }
            if (canEdit) {
                DropdownMenuItem(
                    text = { OverflowLabel(stringResource(R.string.action_edit)) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                )
            }
        }
    }
}

@Composable
private fun OverflowLabel(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
    )
}
