package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.ListItemDefaults
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** The cover's height on an edit screen, carried from iOS. */
val EditCoverHeight = 126.dp

/** The avatar's diameter on an edit screen, carried from iOS. */
val EditAvatarSize = 68.dp

private val BadgeSize = 28.dp
private val BadgeIconSize = 16.dp
private val BadgeOffsetX = 6.dp
private val BadgeOffsetY = 2.dp

/**
 * The cover card with the avatar hanging off its bottom edge, half over it, as the edit screens
 * for a profile and for a group draw it.
 *
 * Takes what to draw and how to caption it, never ids or controllers: the owner of the screen
 * decides what the pictures are and where a tap goes. [access] is the surface both pictures are
 * read from. The cover is clipped to the medium shape inside the caller's content padding.
 */
@Composable
fun CoverAndPhoto(
    cover: MediaItem?,
    avatar: MediaItem?,
    displayName: String,
    access: BlobAccessContext,
    changeCoverLabel: String,
    changePhotoLabel: String,
    onChangeCover: () -> Unit,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    coverHeight: Dp = EditCoverHeight,
    avatarSize: Dp = EditAvatarSize,
) {
    val grid = CodeTheme.dimens.staticGrid
    // The cover is what the Change cover chip frosts, as iOS's CoverChip is glass over it.
    val hazeState = rememberHazeState()
    Box(modifier = modifier.fillMaxWidth().height(coverHeight + avatarSize / 2)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(CodeTheme.shapes.medium)
                .clickable(onClick = onChangeCover),
        ) {
            ProfileCover(
                image = cover,
                access = access,
                height = Dp.Unspecified,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            )
            Text(
                text = changeCoverLabel,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(grid.x2)
                    .floatingGlass(hazeState)
                    .padding(horizontal = grid.x2, vertical = grid.x1),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = grid.x3)
                .clickable(onClick = onChangePhoto),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(grid.x2),
        ) {
            Box {
                ContactAvatar(
                    image = avatar,
                    displayName = displayName,
                    access = access,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(grid.x1, CodeTheme.colors.background, CircleShape),
                )
                CameraBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = BadgeOffsetX, y = BadgeOffsetY),
                )
            }
            Text(
                text = changePhotoLabel,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = grid.x1),
            )
        }
    }
}

@Composable
fun CameraBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BadgeSize)
            .clip(CircleShape)
            // Opaque, so the avatar doesn't show through the tinted fill.
            .background(CodeTheme.colors.background)
            .background(White05),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(BadgeIconSize),
        )
    }
}

/**
 * A tappable card: the field's title over its current value, with the disclosure chevron.
 *
 * A null [value] shows [placeholder] in the secondary colour; [valueIsPrompt] does the same for a
 * value that is itself a call to action. [maxLines] is 1 for a name and 3 for a description.
 */
@Composable
fun FieldCard(
    title: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueIsPrompt: Boolean = false,
    maxLines: Int = 1,
) {
    val grid = CodeTheme.dimens.staticGrid
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.medium)
            .background(White05)
            .clickable(onClick = onClick)
            .padding(horizontal = grid.x3, vertical = grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(grid.x2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = value ?: placeholder,
                style = CodeTheme.typography.textMedium,
                color = if (value == null || valueIsPrompt) {
                    CodeTheme.colors.textSecondary
                } else {
                    CodeTheme.colors.textMain
                },
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ListItemDefaults.Chevron()
    }
}
