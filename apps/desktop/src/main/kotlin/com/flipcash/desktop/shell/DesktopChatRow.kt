package com.flipcash.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.shared.chat.ui.ChatRowSubtitle
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.JoinRequestSubtitle
import com.flipcash.shared.chat.ui.MutedIndicator
import com.flipcash.shared.chat.ui.UnreadBadge
import com.flipcash.shared.chat.ui.formatLastActivity
import com.flipcash.shared.chat.ui.joinRequestText
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White

/** White at 12%: the selected row and the chips' fill (Figma `chip/fill`). No theme token sits there. */
internal val ChipFill = White.copy(alpha = 0.12f)

/** The preview line is 13 Regular in the design; the theme has 14 SemiBold (`textSmall`) as its nearest. */
internal val PreviewStyle @Composable get() = CodeTheme.typography.textSmall.copy(
    fontSize = 13.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 16.sp,
)

/**
 * A row of the sidebar (node 13000:115): 60 tall, a filled rounded background when it is the open
 * chat and a lighter one under the pointer, no chevron and no divider. The phone's `TipChatRow` draws
 * both and has no selected state, so this lays out its own row from the same pieces: the avatar, the
 * time formatter, the preview rules and the unread pill.
 */
@Composable
fun DesktopChatRow(
    chat: ConversationReference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val fill = when {
        selected -> ChipFill
        hovered -> White05
        else -> Color.Transparent
    }
    val unread = chat.rowUnreadCount
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .hoverable(hover)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(10.dp)
            .testTag("chat_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier.requiredSize(40.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = chat.name.orEmpty(),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MutedIndicator(viewerState = chat.viewerState)
                }
                chat.rowActivity?.let { at ->
                    Text(
                        text = formatLastActivity(at),
                        style = CodeTheme.typography.caption,
                        // The unread colour is the badge's: blue says there is something new.
                        color = if (unread != 0) CodeTheme.colors.indicator else CodeTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val request = joinRequestText(chat.joinRequests)
                    if (request != null && !chat.isTyping) {
                        JoinRequestSubtitle(
                            request = request,
                            preview = chat.lastMessagePreview.takeIf { chat.unreadCount != 0 },
                            style = PreviewStyle,
                        )
                    } else {
                        ChatRowSubtitle(
                            isTyping = chat.isTyping,
                            preview = chat.lastMessagePreview,
                            hasMessages = chat.hasMessages,
                            awaitsInvites = chat.awaitsInvites,
                            style = PreviewStyle,
                        )
                    }
                }
                if (unread != 0) UnreadBadge(count = unread)
            }
        }
    }
}
