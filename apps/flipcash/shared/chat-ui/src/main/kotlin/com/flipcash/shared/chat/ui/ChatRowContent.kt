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
import androidx.compose.ui.text.font.FontStyle
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
        // No start padding: the title row's spacing already puts the design's 4dp between the
        // timestamp and the pill.
        UnreadBadge(
            modifier = Modifier.padding(end = CodeTheme.dimens.grid.x1),
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
 * Subtitle line of a chat row: the typing indicator, else the message [preview], else a placeholder
 * when the chat has no messages at all ([hasMessages]), else the caller's [fallback] (e.g. a phone
 * number or a "joined" line for a contact).
 *
 * The placeholder is keyed on [hasMessages] rather than on the absent [preview] because the two are
 * not the same thing: a chat whose newest message is media or a system event previews as nothing
 * but is not empty, and telling the viewer it is would be a lie. Rows that cannot tell the
 * difference leave [hasMessages] alone and keep their own fallback.
 */
@Composable
fun ChatRowSubtitle(
    isTyping: Boolean,
    preview: String?,
    hasMessages: Boolean = true,
    fallback: @Composable () -> Unit = {},
) {
    when {
        isTyping -> SubtitleText(stringResource(R.string.label_isTyping))
        !preview.isNullOrEmpty() -> SubtitleText(preview)
        !hasMessages -> SubtitleText(
            text = stringResource(R.string.label_chat_preview_noMessages),
            // Italic marks it as the row talking about itself rather than quoting a message —
            // the same distinction the tombstone bubble draws.
            fontStyle = FontStyle.Italic,
        )
        else -> fallback()
    }
}

/** Standard single-line secondary text used for chat/contact row subtitles. */
@Composable
fun SubtitleText(text: String, fontStyle: FontStyle = FontStyle.Normal) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        fontStyle = fontStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
