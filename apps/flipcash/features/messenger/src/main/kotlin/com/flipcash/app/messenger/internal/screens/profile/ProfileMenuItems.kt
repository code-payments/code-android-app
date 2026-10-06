package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * The row's string. Muting names the opposite once the chat is muted; the picker it opens is where
 * the duration is chosen either way.
 */
@androidx.annotation.StringRes
internal fun ChatProfileAction.labelRes(isMuted: Boolean): Int = when (this) {
    ChatProfileAction.Mute -> if (isMuted) R.string.title_unmuteChat else R.string.title_muteChat
    ChatProfileAction.Report -> R.string.title_report
    ChatProfileAction.Block -> R.string.title_block
    ChatProfileAction.Unblock -> R.string.action_unblock
}

/** The ⋯ button's menu, rows in [items]' order. */
@Composable
internal fun ProfileMenu(
    expanded: Boolean,
    items: List<ChatProfileAction>,
    isMuted: Boolean,
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
        items.forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(item.labelRes(isMuted)),
                        style = CodeTheme.typography.textSmall,
                        color = if (item.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain,
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
