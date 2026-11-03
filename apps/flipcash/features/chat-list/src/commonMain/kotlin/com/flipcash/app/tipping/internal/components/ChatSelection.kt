package com.flipcash.app.tipping.internal.components

import com.flipcash.core.resources.action_read
import com.flipcash.core.resources.action_unread
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.remember
import com.flipcash.services.models.chat.ChatId
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_markAsRead
import com.flipcash.core.resources.action_markAsUnread
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SwipeAction
import com.getcode.util.resources.Res as UiRes
import com.getcode.util.resources.ic_mark_chat_read
import com.getcode.util.resources.ic_mark_chat_unread
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Select mode's circles, shared by every row on a list: how far they have slid in, and which are
 * checked. Held by the screen rather than each row, because a row is rebuilt when select mode drops
 * its swipe wrapper, which would restart a slide kept inside it.
 */
internal class SelectCircles(val reveal: () -> Float, val checked: Set<ChatId>)

@Composable
internal fun rememberSelectCircles(selection: Set<ChatId>?): SelectCircles {
    val reveal = animateFloatAsState(if (selection != null) 1f else 0f, label = "select circles")
    // Leaving select mode drops the selection while the circles are still sliding out, so they keep
    // drawing the one they last had. Not state: it only changes when `selection` already has.
    val last = remember { arrayOf(emptySet<ChatId>()) }
    if (selection != null) last[0] = selection
    return SelectCircles(reveal = { reveal.value }, checked = last[0])
}

/**
 * The leading swipe on a chat list row: Mark as Read on an unread chat, Mark as Unread otherwise.
 * Tonal like the trailing Mute circle: the glyph in the unread blue on its dark-blue ground, sized
 * like the trailing Archive and Mute circles. The title under it is the short Read / Unread; the
 * content description keeps the long Mark as Read / Mark as Unread.
 */
@Composable
internal fun markReadSwipeAction(isUnread: Boolean, onTriggered: () -> Unit): Pair<SwipeAction, String> {
    val label = stringResource(if (isUnread) Res.string.action_markAsRead else Res.string.action_markAsUnread)
    val title = stringResource(if (isUnread) Res.string.action_read else Res.string.action_unread)
    val painter = painterResource(
        if (isUnread) UiRes.drawable.ic_mark_chat_read else UiRes.drawable.ic_mark_chat_unread
    )
    return SwipeAction(
        background = CodeTheme.colors.indicatorSecondary,
        onTriggered = onTriggered,
        // The row stays in the list, so a full swipe has to send it home rather than leave it
        // settled off screen.
        resetOnDismiss = true,
        label = title,
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = CodeTheme.colors.indicator,
            modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
        )
    } to label
}
