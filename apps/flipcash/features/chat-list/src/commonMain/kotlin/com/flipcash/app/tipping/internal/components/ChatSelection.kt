package com.flipcash.app.tipping.internal.components

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
 * The leading swipe on a chat list row: Mark as Read on an unread chat, Mark as Unread otherwise.
 * A circle in the unread dot's colour, sized like the trailing Archive and Mute circles.
 */
@Composable
internal fun markReadSwipeAction(isUnread: Boolean, onTriggered: () -> Unit): Pair<SwipeAction, String> {
    val label = stringResource(if (isUnread) Res.string.action_markAsRead else Res.string.action_markAsUnread)
    val painter = painterResource(
        if (isUnread) UiRes.drawable.ic_mark_chat_read else UiRes.drawable.ic_mark_chat_unread
    )
    return SwipeAction(
        background = CodeTheme.colors.indicator,
        onTriggered = onTriggered,
        // The row stays in the list, so a full swipe has to send it home rather than leave it
        // settled off screen.
        resetOnDismiss = true,
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
        )
    } to label
}
