package com.flipcash.app.tipping.internal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.flipcash.shared.chat.ui.ChatListRow
import com.flipcash.shared.chat.ui.ChatRowSubtitle
import com.flipcash.shared.chat.ui.ChatRowTrailing
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.MutedIndicator
import com.flipcash.shared.chat.ui.SubtitleText
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme


@Composable
internal fun TipChatRow(
    chat: ConversationReference,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    ChatListRow(
        modifier = modifier,
        avatar = {
            ContactAvatar(
                image = chat.image,
                displayName = chat.name.orEmpty(),
                access = chat.avatarAccess,
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape),
            )
        },
        title = {
            Text(
                modifier = Modifier.weight(1f),
                // Name, or the `@handle` when there isn't one — the row's single line of identity
                // (node 9442:103645 has the preview under it, so there is nowhere else to put it).
                text = chat.name.orEmpty(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )

            ChatRowTrailing(
                lastActivity = chat.lastActivity,
                unreadCount = chat.unreadCount,
                canOpen = true,
            )
        },
        subtitle = {
            // The mute sits at the end of this line rather than the one above it, where the
            // timestamp and unread badge already compete for the trailing corner. It reads as the
            // bottom of a column of chat-level state, and matches where iOS draws it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // An audible chat emits no indicator at all, so the gap is not spent either and
                // the preview keeps the full width.
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ChatRowSubtitle(
                        isTyping = chat.isTyping,
                        preview = chat.lastMessagePreview,
                        fallback = {
                            SubtitleText("")
                        }
                    )
                }

                MutedIndicator(viewerState = chat.viewerState)
            }
        },
        showDivider = showDivider,
        onClick = onClick,
    )
}