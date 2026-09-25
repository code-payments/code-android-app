package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.ChatInputSubmit
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
        val hazeState = rememberHazeState()
        val density = LocalDensity.current
        var composerHeight by remember { mutableStateOf(0.dp) }

        // The list runs the rest of the sheet and scrolls under the message bar, which blurs what
        // passes behind it more the lower it goes (node 10330:19709), rather than stopping above it.
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(
                    bottom = if (state.showsComposer) composerHeight else 0.dp,
                ),
            ) {
                if (recentChats.isNotEmpty()) {
                    item(key = "header") { RecentChatsHeader() }
                }
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { composerHeight = with(density) { it.height.toDp() } },
                    hazeState = hazeState,
                    sending = state.sending,
                    onMessageChanged = onMessageChanged,
                    onInvite = onInvite,
                )
            }
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

/** Node 10330:19568 — the list's label, ruled off from the rows below it. */
@Composable
private fun RecentChatsHeader() {
    Column(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
        Text(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.label_recentChats),
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        HorizontalDivider(color = CodeTheme.colors.divider)
    }
}

/**
 * Node 10330:19387 — one 1:1 chat, picked or not. The whole row takes the tap. Its divider starts
 * at the name, not the avatar, as in node 10330:19585.
 */
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
            .padding(horizontal = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The same mark the token and currency pickers use, so this reads as the same kind
                // of list.
                Image(
                    painter = painterResource(
                        if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
                    ),
                    contentDescription = null,
                )
            }
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}

/**
 * Node 10329:11963 — the message to go with the link, and the send. Only drawn once a chat is
 * picked; the field keeps its own text, and the sheet's view model reads it at send time.
 *
 * The chat screen's own composer, so typing here feels like typing in a chat, with an Invite label
 * in place of the send arrow because the link goes out whether or not a message is typed. Behind
 * it, the list blurs in from nothing at the top edge to full at the bottom.
 */
@Composable
private fun InviteComposer(
    hazeState: HazeState,
    sending: Boolean,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = rememberTextFieldState()
    LaunchedEffect(message) {
        snapshotFlow { message.text.toString() }.collect(onMessageChanged)
    }
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = material.then {
                    progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
                },
            )
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x2),
    ) {
        ChatInput(
            modifier = Modifier.border(
                CodeTheme.dimens.border,
                CodeTheme.colors.divider,
                CodeTheme.shapes.medium,
            ),
            enabled = !sending,
            hint = stringResource(R.string.hint_addAMessage),
            state = message,
            submit = ChatInputSubmit.Action(label = stringResource(R.string.action_invite)) {
                onInvite()
            },
        )
    }
}

// Node 10330:19572: the gap between avatar and name, which is also where the row's divider starts.
private val RowGap = 16.dp
