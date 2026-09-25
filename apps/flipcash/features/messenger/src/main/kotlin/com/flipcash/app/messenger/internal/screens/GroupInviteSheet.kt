package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.media.rememberMediaUrl
import com.flipcash.app.core.share.SharePreviewImage
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.messenger.internal.screens.profile.ProfileShortcut
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.theme.inputColors
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TextInput
import kotlinx.coroutines.launch

/**
 * Node 10329:12104 — handing out the link to a group, and sending it straight into 1:1 chats.
 *
 * The Share and Copy tiles carry the same URL, built once by
 * [com.flipcash.app.core.util.Linkify.groupChatInvite] from the chat's id: there is no invite RPC,
 * the id *is* the invite, and one builder is what keeps the shared link, the copied link and the
 * sent link identical.
 *
 * @param inviteUrl null for anyone who has nobody to invite — a DM, or a group this viewer has not
 * joined — in which case the sheet is the title bar alone. Both entry points are gated on the same
 * value, so this is a race with a leave rather than a state to design for.
 * @param group what is being invited to, for the group's name and picture. The sheet only opens on
 * a group, so null is the frame before the subject resolves.
 * @param state the Recent Chats selection and the message to send with the link.
 */
@Composable
internal fun GroupInviteSheet(
    inviteUrl: String?,
    group: ChatSubject.Group?,
    state: GroupInviteViewModel.State,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onToggle: (ChatId) -> Unit,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shareController = LocalShareController.current
    val scope = rememberCoroutineScope()

    // Resolved here rather than in the share controller: the stored download URL expires, and
    // re-minting it needs the chat's profile to authorize it — neither of which the shareable
    // module can see. Same size and access as the avatar the conversation bar draws.
    val picture = group?.picture
    val pictureUrl = rememberMediaUrl(
        media = picture,
        targetLongestSidePx = SharePreviewImage.TARGET_PX,
        access = group?.chatId
            ?.let { BlobAccessContext.ChatProfile(it) }
            ?: BlobAccessContext.Owned,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_invitePeople),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        if (inviteUrl == null) return@Column

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x4),
            horizontalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.staticGrid.x3,
                Alignment.CenterHorizontally,
            ),
        ) {
            ProfileShortcut(
                label = stringResource(R.string.action_shareInviteLink),
                onClick = {
                    onShare()
                    scope.launch {
                        shareController.present(
                            Shareable.GroupInvite(
                                url = inviteUrl,
                                title = group?.groupTitle,
                                imageUrl = pictureUrl.url,
                                imageCacheKey = picture
                                    ?.cacheKeyForSize(SharePreviewImage.TARGET_PX),
                            )
                        )
                    }
                },
            ) {
                ShortcutIcon(R.drawable.ic_share_os)
            }
            ProfileShortcut(
                label = stringResource(R.string.action_copyInviteLink),
                onClick = onCopy,
            ) {
                ShortcutIcon(R.drawable.ic_copy)
            }
        }

        val recentChats = state.recentChats.orEmpty()
        if (recentChats.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x2,
                ),
                text = stringResource(R.string.label_recentChats),
                style = CodeTheme.typography.textSmall,
                color = White50,
            )
        }

        // Takes what is left of the sheet without claiming it, so a short list does not push the
        // message bar to the bottom of the screen.
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(recentChats, key = { it.chatId.toString() }) { chat ->
                RecentChatRow(
                    chat = chat,
                    selected = chat.chatId in state.selection,
                    enabled = !state.sending,
                    onClick = { onToggle(chat.chatId) },
                )
            }
        }

        if (state.showsComposer) {
            InviteComposer(
                sending = state.sending,
                onMessageChanged = onMessageChanged,
                onInvite = onInvite,
            )
        }
    }
}

@Composable
private fun ShortcutIcon(icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = CodeTheme.colors.textMain,
        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
    )
}

/** Node 10330:19387 — one 1:1 chat, picked or not. The whole row takes the tap. */
@Composable
private fun RecentChatRow(
    chat: ConversationReference,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = chat.name.orEmpty(),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The same mark the token and currency pickers use, so this reads as the same kind of list.
        Image(
            painter = painterResource(
                if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
            ),
            contentDescription = null,
        )
    }
}

/**
 * Node 10329:11963 — the message to go with the link, and the send. Only drawn once a chat is
 * picked; the field keeps its own text, and the sheet's view model reads it at send time.
 *
 * One pill holds both: the field draws no box of its own, so the hint sits on the pill's inset
 * like the design's bare "Add a message" label rather than in a second rounded field.
 */
@Composable
private fun InviteComposer(
    sending: Boolean,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
) {
    val message = rememberTextFieldState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2)
            .clip(ComposerShape)
            .background(White05)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        TextInput(
            modifier = Modifier.weight(1f),
            state = message,
            placeholder = stringResource(R.string.hint_addAMessage),
            enabled = !sending,
            minHeight = InviteButtonHeight,
            colors = inputColors(
                borderColor = Color.Transparent,
                backgroundColor = Color.Transparent,
                placeholderColor = CodeTheme.colors.textMain.copy(alpha = 0.4f),
            ),
            onStateChanged = { onMessageChanged(message.text.toString()) },
        )
        Box(
            modifier = Modifier
                .height(InviteButtonHeight)
                .clip(InviteButtonShape)
                .background(White)
                .clickable(enabled = !sending, role = Role.Button, onClick = onInvite)
                .padding(horizontal = CodeTheme.dimens.grid.x3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.action_invite),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.background,
            )
        }
    }
}

// Node 10329:11963's measurements. The theme has no 14dp shape, and the button's 34dp height is
// what lines the field's text up with the button's label.
private val ComposerShape = RoundedCornerShape(14.dp)
private val InviteButtonShape = RoundedCornerShape(6.dp)
private val InviteButtonHeight = 34.dp
