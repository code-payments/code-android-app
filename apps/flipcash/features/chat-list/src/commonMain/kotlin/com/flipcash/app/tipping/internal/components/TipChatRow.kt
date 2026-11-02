package com.flipcash.app.tipping.internal.components

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.subtitle_peopleWantToJoin
import com.flipcash.core.resources.subtitle_wantsToJoin
import com.flipcash.core.resources.title_unnamedUser
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
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
    // Select mode: whether this row is picked, or null outside select mode. In it a circle leads
    // the row, the content shifts right to make room, and the chevron goes (a tap toggles).
    selected: Boolean? = null,
    // How far select mode's circle has slid in (0 to 1) and whether it is checked. The screen
    // drives both so the slide survives the row being rebuilt when select mode starts or ends, and
    // a circle sliding out keeps the state it had.
    circleReveal: () -> Float = { if (selected != null) 1f else 0f },
    circleChecked: Boolean = selected == true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val selectedTint = CodeTheme.colors.surfaceVariant
    ChatListRow(
        modifier = modifier.drawWithContent {
            drawContent()
            // Over the row rather than behind it: the shared row paints its own opaque background.
            if (selected == true) drawRect(selectedTint)
        },
        avatar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectCircleSlot(circleReveal, circleChecked)
                ContactAvatar(
                    image = chat.image,
                    displayName = chat.name.orEmpty(),
                    access = chat.avatarAccess,
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.staticGrid.x9)
                        .clip(CircleShape),
                )
            }
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
                canOpen = selected == null,
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
                awaitsInvites = chat.awaitsInvites,
                fallback = {
                    // Blank rather than absent: a chat with a message the row cannot preview
                    // still occupies both lines, so it doesn't jump in height against its
                    // neighbours.
                    SubtitleText("")
                }
            )
        },
        showDivider = showDivider,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/**
 * Select mode's leading circle. It slides in, pushing the row content right, only as select mode
 * starts ([reveal] going 0 to 1), and back out as it ends; toggling a row changes the circle in
 * place. Reads [reveal] in layout and draw only, so the slide doesn't recompose the row.
 */
@Composable
private fun SelectCircleSlot(reveal: () -> Float, checked: Boolean) {
    Box(
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * reveal()).roundToInt()
                layout(width, placeable.height) { placeable.placeRelative(width - placeable.width, 0) }
            }
            .graphicsLayer { alpha = reveal() }
    ) {
        // The gap to the avatar slides with the circle rather than popping in ahead of it.
        Box(Modifier.padding(end = CodeTheme.dimens.staticGrid.x3)) { SelectCircle(checked) }
    }
}

/**
 * An outline in the secondary text colour when not picked, a white check on the unread blue when
 * picked. Both fill the same circle, so a toggle only crossfades between them.
 */
@Composable
private fun SelectCircle(selected: Boolean) {
    val size = CodeTheme.dimens.staticGrid.x4
    Crossfade(targetState = selected, label = "select circle") { picked ->
        if (picked) {
            Box(
                modifier = Modifier
                    .requiredSize(size)
                    .background(CodeTheme.colors.indicator, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x3),
                )
            }
        } else {
            Box(
                Modifier
                    .requiredSize(size)
                    .border(1.5.dp, CodeTheme.colors.textSecondary, CircleShape)
            )
        }
    }
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
            Res.string.subtitle_wantsToJoin,
            chatterName(
                only.userProfile.displayName,
                only.userProfile.username,
                stringResource(Res.string.title_unnamedUser),
            ),
        )
        else -> pluralStringResource(Res.plurals.subtitle_peopleWantToJoin, waiting.size, waiting.size)
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
