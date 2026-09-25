package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.media.rememberMediaUrl
import com.flipcash.app.core.share.SharePreviewImage
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow
import kotlinx.coroutines.launch

/**
 * Node 10127:118315 — the two ways to hand out the link to a group.
 *
 * Both actions carry the same URL, built once by
 * [com.flipcash.app.core.util.Linkify.groupChatInvite] from the chat's id: there is no invite RPC,
 * the id *is* the invite, and one builder is what keeps the shared link and the copied link
 * identical.
 *
 * @param inviteUrl null for anyone who has nobody to invite — a DM, or a group this viewer has not
 * joined — in which case the sheet is the title bar alone. Both entry points are gated on the same
 * value, so this is a race with a leave rather than a state to design for.
 * @param group what is being invited to, for the group's name and picture. The sheet only opens on
 * a group, so null is the frame before the subject resolves.
 */
@Composable
internal fun GroupInviteSheet(
    inviteUrl: String?,
    group: ChatSubject.Group?,
    onShare: () -> Unit,
    onCopy: () -> Unit,
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
            .navigationBarsPadding(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_inviteToJoinGroup),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        if (inviteUrl != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x4),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                ChoiceRow(
                    label = stringResource(R.string.action_sendInviteLink),
                    icon = R.drawable.ic_at,
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
                )
                ChoiceRow(
                    label = stringResource(R.string.action_copyInviteLink),
                    icon = R.drawable.ic_copy,
                    onClick = onCopy,
                )
            }
        }
    }
}
