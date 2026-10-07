package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge

/**
 * What a DM counterparty's profile can act on.
 *
 * Its own type rather than [ChatProfileViewModel.Event] because the rows do different kinds of
 * thing and answer to different places. Blocking is this screen's own business, while muting is
 * the conversation's, so naming the actions here lets one menu carry both and the screen route
 * each tap to whichever holds it.
 */
internal sealed interface ChatProfileAction {
    data object Block : ChatProfileAction
    data object Mute : ChatProfileAction
    data object Report : ChatProfileAction
    data object Unblock : ChatProfileAction
}

/** Report and Block render in the destructive colour. */
internal val ChatProfileAction.isDestructive: Boolean
    get() = this == ChatProfileAction.Report || this == ChatProfileAction.Block

/**
 * The row's string. Muting reads "Mute Notifications" whether or not the chat is muted, as on iOS;
 * the sheet it opens handles both muting and unmuting.
 */
@androidx.annotation.StringRes
internal fun ChatProfileAction.labelRes(): Int = when (this) {
    ChatProfileAction.Mute -> R.string.title_muteChat
    ChatProfileAction.Report -> R.string.title_report
    ChatProfileAction.Block -> R.string.title_block
    ChatProfileAction.Unblock -> R.string.action_unblock
}

/** The row's glyph, matching iOS' `bell.slash`, `exclamationmark.bubble`, `nosign` and `checkmark.circle`. */
internal fun ChatProfileAction.icon(): ImageVector = when (this) {
    ChatProfileAction.Mute -> Icons.Outlined.NotificationsOff
    ChatProfileAction.Report -> Icons.Outlined.Feedback
    ChatProfileAction.Block -> Icons.Outlined.Block
    ChatProfileAction.Unblock -> Icons.Outlined.CheckCircle
}

/** The ⋯ button's menu, rows in [items]' order. */
@Composable
internal fun ProfileMenu(
    expanded: Boolean,
    items: List<ChatProfileAction>,
    anchorHeight: Dp,
    onDismiss: () -> Unit,
    onItem: (ChatProfileAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // Same surface as the chat's overflow menu, opened over the button that anchors it.
        containerColor = CodeTheme.colors.brandLight,
        shape = CodeTheme.shapes.extraLarge,
        offset = DpOffset(x = 0.dp, y = -anchorHeight),
    ) {
        items.forEachIndexed { index, item ->
            // A divider wherever the red rows start or stop, so they sit apart, as on iOS.
            if (index > 0 && items[index - 1].isDestructive != item.isDestructive) {
                HorizontalDivider(color = CodeTheme.colors.divider)
            }
            val tint = if (item.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain
            DropdownMenuItem(
                leadingIcon = {
                    Icon(imageVector = item.icon(), contentDescription = null, tint = tint)
                },
                text = {
                    Text(
                        text = stringResource(item.labelRes()),
                        style = CodeTheme.typography.textSmall,
                        color = tint,
                    )
                },
                onClick = {
                    onDismiss()
                    onItem(item)
                },
            )
        }
    }
}
