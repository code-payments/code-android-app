package com.flipcash.app.tipping.internal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.tipping.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.ui.ChatListRow
import com.flipcash.shared.chat.ui.ChatRowSubtitle
import com.flipcash.shared.chat.ui.ChatRowTrailing
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.JoinRequestSubtitle
import com.flipcash.shared.chat.ui.MutedIndicator
import com.flipcash.shared.chat.ui.SubtitleText
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.theme.CodeTheme
import kotlin.time.Clock


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
                    .requiredSize(CodeTheme.dimens.staticGrid.x9)
                    .clip(CircleShape),
            )
        },
        title = {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                Text(
                    // Takes only what it needs, so the bell trails the name itself rather than
                    // the far edge, and a long name ellipsizes before it pushes the bell off.
                    modifier = Modifier.weight(1f, fill = false),
                    // Name, or the `@handle` when there isn't one — the row's single line of
                    // identity (node 9442:103645 has the preview under it, so there is nowhere
                    // else to put it).
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // An audible chat emits no indicator at all, so the gap is not spent either.
                MutedIndicator(viewerState = chat.viewerState)
            }

            ChatRowTrailing(
                lastActivity = chat.rowActivity,
                unreadCount = chat.rowUnreadCount,
                canOpen = true,
            )
        },
        subtitle = {
            val request = joinRequestText(chat.joinRequests)
            // Someone typing is live and passes in seconds, so it keeps the slot; the request
            // comes back when they stop.
            if (request != null && !chat.isTyping) {
                JoinRequestSubtitle(
                    request = request,
                    // A read message stays out of the way until every request is answered; only
                    // unread ones share the line.
                    preview = chat.lastMessagePreview.takeIf { chat.unreadCount != 0 },
                )
            } else ChatRowSubtitle(
                isTyping = chat.isTyping,
                preview = chat.lastMessagePreview,
                hasMessages = chat.hasMessages,
                fallback = {
                    // Blank rather than absent: a chat with a message the row cannot preview
                    // still occupies both lines, so it doesn't jump in height against its
                    // neighbours.
                    SubtitleText("")
                }
            )
        },
        showDivider = showDivider,
        onClick = onClick,
    )
}

/**
 * "Jasper wants to join" for one requester, "3 people want to join" for several, or null with
 * nobody waiting. Named the way the in-chat Join Requests anchor names them.
 */
@Composable
private fun joinRequestText(waiting: List<LobbyMember>): String? {
    val only = waiting.singleOrNull()
    return when {
        waiting.isEmpty() -> null
        only != null -> stringResource(
            R.string.subtitle_wantsToJoin,
            chatterName(
                only.userProfile.displayName,
                only.userProfile.username,
                stringResource(R.string.title_unnamedUser),
            ),
        )
        else -> pluralStringResource(R.plurals.subtitle_peopleWantToJoin, waiting.size, waiting.size)
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewTipChatRowUnread() {
    val now = Clock.System.now()
    Column {
        TipChatRow(
            chat = ConversationReference(
                chatId = ChatId(byteArrayOf(1)),
                displayName = "Sally The Streamer",
                lastMessagePreview = "Thanks!",
                lastActivity = now,
            ),
            onClick = {},
        )
        TipChatRow(
            chat = ConversationReference(
                chatId = ChatId(byteArrayOf(2)),
                displayName = "Grace Hopper",
                lastMessagePreview = "Sent you $1.00 in Dollars",
                lastActivity = now,
                unreadCount = 2,
            ),
            onClick = {},
        )
        TipChatRow(
            chat = ConversationReference(
                chatId = ChatId(byteArrayOf(3)),
                displayName = "Moony",
                lastMessagePreview = "Chloe Anderson: Agreed!",
                lastActivity = now,
                unreadCount = 15,
            ),
            showDivider = false,
            onClick = {},
        )
    }
}
