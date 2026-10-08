package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme

private val AvatarSize = 84.dp
private val AvatarRing = 5.dp
private val AvatarOverlap = 42.dp

/**
 * The top of a profile: [cover], an avatar overlapping it, the actions beside the avatar, and the
 * subject's [title], [subtitle] and [body]. A null [title] (an account with no name yet) draws
 * nothing in its place.
 *
 * It renders a subject, not a user — a person today, a group next — so callers map their model to
 * these inputs. [avatar] receives the size, clip and ring to draw with, which keeps the avatar the
 * caller's (a user's picture, a group's) while the overlap stays the header's. [underSubtitle]
 * holds anything that belongs between the handle and the bio. [onCover] sits on the cover's
 * bottom edge beside the avatar; the cover is a fixed height, so content there (a status chip that
 * arrives late) never moves the rest of the header.
 *
 * [cover] is laid out edge to edge from the top of its parent and is not padded for the status
 * bar; the page gutter applies to everything below it.
 */
@Composable
fun ProfileHeader(
    cover: MediaItem?,
    access: BlobAccessContext,
    avatar: @Composable (Modifier) -> Unit,
    title: String?,
    subtitle: String?,
    body: String?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    underSubtitle: @Composable () -> Unit = {},
    onCover: @Composable RowScope.() -> Unit = {},
) {
    val inset = CodeTheme.dimens.inset
    Box(modifier = modifier.fillMaxWidth()) {
        ProfileCover(image = cover, access = access)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProfileCoverHeight)
                .padding(
                    start = inset + AvatarSize + CodeTheme.dimens.staticGrid.x3,
                    end = inset,
                    bottom = CodeTheme.dimens.staticGrid.x2,
                ),
            verticalAlignment = Alignment.Bottom,
            content = onCover,
        )
        Column(modifier = Modifier.padding(top = ProfileCoverHeight - AvatarOverlap)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.Top,
            ) {
                avatar(
                    Modifier
                        .size(AvatarSize)
                        // A solid disc behind an inset clip, not a border over the clip: a border
                        // shares the picture's edge, so the picture's antialiased fringe shows
                        // past the ring.
                        .background(CodeTheme.colors.background, CircleShape)
                        .padding(AvatarRing)
                        .clip(CircleShape)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = AvatarOverlap + CodeTheme.dimens.staticGrid.x4),
                    horizontalArrangement = Arrangement.spacedBy(
                        CodeTheme.dimens.staticGrid.x3,
                        Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            // iOS sets the text 8pt below the action row.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inset)
                    .padding(top = CodeTheme.dimens.staticGrid.x2),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = CodeTheme.typography.displaySmall,
                        color = CodeTheme.colors.textMain,
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                underSubtitle()
                if (!body.isNullOrBlank()) {
                    Text(
                        modifier = Modifier.padding(top = CodeTheme.dimens.staticGrid.x3),
                        text = body,
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(withBody: Boolean, withChip: Boolean) {
    ProfileHeader(
        cover = null,
        access = BlobAccessContext.Owned,
        avatar = { ContactAvatar(image = null, displayName = "Ada Lovelace", access = BlobAccessContext.Owned, modifier = it) },
        title = "Ada Lovelace",
        subtitle = "@ada",
        body = "Analytical engines and poetry.".takeIf { withBody },
        actions = {
            ProfileActionButton(text = "Edit Profile", onClick = {})
            ProfileActionButton(icon = Icons.Outlined.IosShare, contentDescription = "Share", onClick = {})
        },
        underSubtitle = {
            if (withChip) {
                ProfileStatusChip(
                    modifier = Modifier.padding(top = CodeTheme.dimens.staticGrid.x2),
                    icon = Icons.Outlined.NotificationsOff,
                    text = "Muted",
                )
            }
        },
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileHeader_NoCover_WithBio() = PreviewHeader(withBody = true, withChip = false)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileHeader_NoCover_NoBio() = PreviewHeader(withBody = false, withChip = true)
