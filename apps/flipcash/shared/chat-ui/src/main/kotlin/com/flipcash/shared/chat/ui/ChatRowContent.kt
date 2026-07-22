package com.flipcash.shared.chat.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import kotlin.time.Instant

/**
 * Trailing cluster of a chat row's title line — reusable by any conversation-style row
 * (contacts, tip chats, …): the last-activity timestamp, then an unread badge, or an
 * open-chevron when [canOpen] and there's nothing unread.
 *
 * Relies on [UnreadBadge] and [formatLastActivity] from `ChatListRow`.
 */
@Composable
fun RowScope.ChatRowTrailing(
    lastActivity: Instant?,
    unreadCount: Int,
    canOpen: Boolean,
) {
    if (lastActivity != null) {
        val activityTextColor by animateColorAsState(
            if (unreadCount > 0) CodeTheme.colors.indicator else CodeTheme.colors.textSecondary
        )
        Text(
            text = formatLastActivity(lastActivity),
            style = CodeTheme.typography.caption,
            color = activityTextColor,
        )
    }

    if (unreadCount > 0) {
        UnreadBadge(
            modifier = Modifier.padding(
                start = CodeTheme.dimens.grid.x1,
                end = CodeTheme.dimens.grid.x1,
            ),
            count = unreadCount,
        )
    } else if (canOpen) {
        Icon(
            modifier = Modifier.scale(0.6f),
            painter = painterResource(com.getcode.util.resources.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}

/**
 * Subtitle line of a chat row: the typing indicator, else the message [preview], else the
 * caller's [fallback] (e.g. a phone number or a "joined" line for a contact).
 */
@Composable
fun ChatRowSubtitle(
    isTyping: Boolean,
    preview: String?,
    fallback: @Composable () -> Unit = {},
) {
    when {
        isTyping -> SubtitleText(stringResource(R.string.label_isTyping))
        !preview.isNullOrEmpty() -> SubtitleText(preview)
        else -> fallback()
    }
}

/** Standard single-line secondary text used for chat/contact row subtitles. */
@Composable
fun SubtitleText(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
