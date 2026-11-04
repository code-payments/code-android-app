// kmp-audit: AppBarWithTitle and CircularIconButton are Android-only; the rows and the read swipe are in commonMain.
package com.flipcash.app.tipping.internal.components
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.tipping.internal.ChatSelectionActions
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_archive
import com.flipcash.core.resources.action_closeChatSelection
import com.flipcash.core.resources.action_markAsRead
import com.flipcash.core.resources.action_markAsUnread
import com.flipcash.core.resources.action_mute
import com.flipcash.core.resources.action_readAll
import com.flipcash.core.resources.action_selectChats
import com.flipcash.core.resources.action_unarchive
import com.flipcash.core.resources.action_unmute
import com.flipcash.core.resources.content_description_moreChatActions
import com.flipcash.core.resources.title_chatsSelected
import com.flipcash.shared.chat.ChatMuteToggle
import com.flipcash.shared.chat.ChatReadToggle
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.util.resources.Res as UiRes
import com.getcode.util.resources.ic_mark_chat_read
import com.getcode.util.resources.ic_mark_chat_unread
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The archive slot of the select-mode bar: Archive on the Chats list, Unarchive on Archived. */
internal enum class SelectionArchiveAction { Archive, Unarchive }

/**
 * The list's title bar, or select mode's [ChatSelectionBar] in its place. Keyed on select mode
 * only, so the count changing inside select mode does not cross-fade the whole bar.
 */
@Composable
internal fun ChatSelectionTopBar(
    selectionActions: ChatSelectionActions?,
    archiveAction: SelectionArchiveAction,
    onClose: () -> Unit,
    onRead: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onArchive: () -> Unit,
    label: String,
    titleBar: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = selectionActions,
        contentKey = { it != null },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = label,
    ) { actions ->
        if (actions != null) {
            ChatSelectionBar(
                actions = actions,
                archiveAction = archiveAction,
                onClose = onClose,
                onRead = onRead,
                onMute = onMute,
                onUnmute = onUnmute,
                onArchive = onArchive,
            )
        } else {
            titleBar()
        }
    }
}

/**
 * Select mode's contextual bar, in place of the title bar: close, the selected count, then the read
 * toggle, mute and archive as icon buttons. Built like the in-chat message selection bar (leading
 * control, title, icon actions in the end slot). The actions sit disabled with nothing selected,
 * which is how Select Chats opens.
 */
@Composable
internal fun ChatSelectionBar(
    actions: ChatSelectionActions,
    archiveAction: SelectionArchiveAction,
    onClose: () -> Unit,
    onRead: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = actions.count > 0
    AppBarWithTitle(
        modifier = modifier,
        leftIcon = {
            SelectionBarButton(
                icon = Icons.Outlined.Close,
                label = stringResource(Res.string.action_closeChatSelection),
                testTag = "action_close_chat_selection",
                onClick = onClose,
            )
        },
        title = {
            Text(
                modifier = Modifier.testTag("chat_selection_count"),
                text = stringResource(Res.string.title_chatsSelected, actions.count),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        rightContents = {
            when (actions.read) {
                ChatReadToggle.MarkRead -> SelectionBarButton(
                    painter = UiRes.drawable.ic_mark_chat_read,
                    label = stringResource(Res.string.action_markAsRead),
                    testTag = "action_mark_selection_read",
                    enabled = enabled,
                    onClick = onRead,
                )
                ChatReadToggle.MarkUnread -> SelectionBarButton(
                    painter = UiRes.drawable.ic_mark_chat_unread,
                    label = stringResource(Res.string.action_markAsUnread),
                    testTag = "action_mark_selection_unread",
                    enabled = enabled,
                    onClick = onRead,
                )
            }
            when (actions.mute) {
                ChatMuteToggle.Mute -> SelectionBarButton(
                    icon = Icons.Outlined.NotificationsOff,
                    label = stringResource(Res.string.action_mute),
                    testTag = "action_mute_selection",
                    enabled = enabled,
                    onClick = onMute,
                )
                ChatMuteToggle.Unmute -> SelectionBarButton(
                    icon = Icons.Outlined.Notifications,
                    label = stringResource(Res.string.action_unmute),
                    testTag = "action_unmute_selection",
                    enabled = enabled,
                    onClick = onUnmute,
                )
            }
            when (archiveAction) {
                SelectionArchiveAction.Archive -> SelectionBarButton(
                    icon = Icons.Outlined.Archive,
                    label = stringResource(Res.string.action_archive),
                    testTag = "action_archive_selection",
                    enabled = enabled,
                    onClick = onArchive,
                )
                SelectionArchiveAction.Unarchive -> SelectionBarButton(
                    icon = Icons.Outlined.Unarchive,
                    label = stringResource(Res.string.action_unarchive),
                    testTag = "action_unarchive_selection",
                    enabled = enabled,
                    onClick = onArchive,
                )
            }
        },
    )
}

@Composable
private fun SelectionBarButton(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: DrawableResource? = null,
    enabled: Boolean = true,
) {
    CircularIconButton(onClick = onClick, enabled = enabled, testTag = testTag) { size ->
        val tint = if (enabled) Color.White else CodeTheme.colors.textSecondary
        val iconModifier = Modifier.requiredSize(size)
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = iconModifier)
        } else if (painter != null) {
            Icon(painter = painterResource(painter), contentDescription = label, tint = tint, modifier = iconModifier)
        }
    }
}

/**
 * The "…" button on the Chats title bar's leading side, opposite `+` and drawn the same way, with
 * its menu: Select Chats, and Read All (disabled when nothing in the main list is unread). The menu
 * surface is the in-chat overflow's.
 */
@Composable
internal fun ChatListMoreButton(
    canReadAll: Boolean,
    onSelectChats: () -> Unit,
    onReadAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircularIconButton(onClick = { expanded = true }, testTag = "action_chat_list_more") { size ->
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(Res.string.content_description_moreChatActions),
                tint = Color.White,
                modifier = Modifier.requiredSize(size),
            )
        }
        DropdownMenu(
            expanded = expanded,
            containerColor = CodeTheme.colors.brandLight,
            shape = CodeTheme.shapes.extraLarge,
            offset = DpOffset(x = 0.dp, y = CodeTheme.dimens.grid.x2),
            onDismissRequest = { expanded = false },
        ) {
            // The app's colors, as ChatTopBar's menu sets them; M3's defaults are dark on this surface.
            val itemColors = MenuDefaults.itemColors(
                textColor = CodeTheme.colors.textMain,
                disabledTextColor = CodeTheme.colors.textSecondary,
            )
            DropdownMenuItem(
                modifier = Modifier.testTag("action_select_chats"),
                colors = itemColors,
                text = { Text(stringResource(Res.string.action_selectChats), style = CodeTheme.typography.textMedium) },
                onClick = {
                    expanded = false
                    onSelectChats()
                },
            )
            DropdownMenuItem(
                modifier = Modifier.testTag("action_read_all"),
                colors = itemColors,
                text = { Text(stringResource(Res.string.action_readAll), style = CodeTheme.typography.textMedium) },
                enabled = canReadAll,
                onClick = {
                    expanded = false
                    onReadAll()
                },
            )
        }
    }
}
