package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.core.chat.ChatParticipant
// Used by the held-back Send Cash shortcut below.
// import com.flipcash.app.messenger.internal.screens.components.CondensedSymbolFontSize
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme

/**
 * Who the profile's Message and Send Cash shortcuts would open a DM with, or null to leave them
 * out.
 *
 * - Not from a tip DM: a DM's profile is always the other person in it, so the shortcuts would
 *   only reopen the chat the viewer came from.
 * - Not for the viewer's own profile: there is no DM with yourself.
 * - Only for a [ChatParticipant.TipUser]: the DM route is addressed by user id, which a contact
 *   doesn't have. Contact DMs don't reach this screen anyway.
 *
 * Blocking isn't checked. The chat the shortcut opens handles a blocked user the way any other
 * entry to it does.
 */
internal fun profileShortcutRecipient(
    participant: ChatParticipant?,
    chatType: ChatType,
    selfId: ID?,
): ChatParticipant.TipUser? {
    if (chatType == ChatType.TIP_DM) return null
    val user = participant as? ChatParticipant.TipUser ?: return null
    if (selfId != null && user.userId == selfId) return null
    return user
}

/**
 * The Message and Send Cash shortcuts under a profile's identity lines.
 *
 * Two fixed-width columns, so the pair stays centered and the circles stay the same distance apart
 * whatever the labels say. The Send Cash glyph is [cashSymbol] rather than an icon, so it names the
 * currency the send will be in, at the size the chat's condensed send button draws it.
 */
@Composable
internal fun ProfileShortcuts(
    cashSymbol: String,
    onMessage: () -> Unit,
    onSendCash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3),
    ) {
        ProfileShortcut(
            label = stringResource(R.string.action_message),
            onClick = onMessage,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_bubble),
                contentDescription = null,
                tint = CodeTheme.colors.textMain,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
            )
        }
        // Send Cash is held back for now. The route flag and the chat's once-ready dispatch behind
        // it stay wired, so bringing it back is uncommenting this.
        // ProfileShortcut(
        //     label = stringResource(R.string.action_sendCash),
        //     onClick = onSendCash,
        // ) {
        //     Text(
        //         text = cashSymbol,
        //         color = CodeTheme.colors.textMain,
        //         style = CodeTheme.typography.textMedium.copy(fontSize = CondensedSymbolFontSize),
        //         maxLines = 1,
        //         softWrap = false,
        //     )
        // }
    }
}

/**
 * One labelled circle: the profile's shortcuts, and the invite sheet's Share and Copy tiles.
 */
@Composable
internal fun ProfileShortcut(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit,
) {
    // The whole column takes the tap, so the label is as much a target as the circle, but only the
    // circle shows the ripple.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(CodeTheme.dimens.staticGrid.x20)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x9)
                .clip(CircleShape)
                .background(CodeTheme.colors.surfaceVariant)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.staticGrid.x1),
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
