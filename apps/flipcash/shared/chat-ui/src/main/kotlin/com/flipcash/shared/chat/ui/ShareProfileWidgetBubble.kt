package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * The share-profile widget as it sits in a transcript: whose profile it is comes from the session,
 * because the widget always describes the signed-in user and carries only [username] on the wire.
 *
 * With no profile in the session (a preview, or the moment before it restores) the name falls back
 * to [username], and the avatar to that name's initials.
 */
@Composable
internal fun ShareProfileWidgetBubble(
    username: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
) {
    val userManager = LocalUserManager.current
    val profile: UserProfile? = userManager?.state?.collectAsState()?.value?.userProfile
    ShareProfileWidgetBubble(
        displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: username,
        username = username,
        profilePicture = profile?.profilePicture,
        userId = profile?.userId ?: userManager?.accountId,
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        onShare = onShare,
        modifier = modifier,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        attention = attention,
    )
}

/** Node 10588:1979. */
@Composable
internal fun ShareProfileWidgetBubble(
    displayName: String,
    username: String,
    profilePicture: MediaItem?,
    userId: ID?,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
) {
    val shape = bubbleShape(position, isFromSelf)
    val width = minOf(maxWidth, ShareProfileWidgetDefaults.WIDTH)
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        minWidth = width,
        maxWidth = width,
        bare = true,
        shape = shape,
        horizontalPadding = CodeTheme.dimens.staticGrid.x3,
        verticalPadding = CodeTheme.dimens.staticGrid.x3,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        modifier = modifier
            .background(White05, shape)
            .border(CodeTheme.dimens.border, ShareProfileWidgetDefaults.STROKE, shape),
        attention = attention,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.SECTION_GAP),
        ) {
            Column(
                modifier = Modifier.widthIn(max = ShareProfileWidgetDefaults.IDENTITY_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3),
            ) {
                ContactAvatar(
                    image = profilePicture,
                    displayName = displayName,
                    access = BlobAccessContext.profile(userId),
                    modifier = Modifier
                        .size(ShareProfileWidgetDefaults.AVATAR)
                        .border(CodeTheme.dimens.border, GroupInviteCardDefaults.STROKE, CircleShape)
                        .clip(CircleShape),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.LINE_GAP),
                ) {
                    Text(
                        text = displayName,
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@$username",
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textMain.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CodeTheme.dimens.staticGrid.x11),
                onClick = { onShare?.invoke() },
                enabled = onShare != null,
                buttonState = ButtonState.Filled,
                shape = RoundedCornerShape(ShareProfileWidgetDefaults.BUTTON_RADIUS),
                overrideContentPadding = true,
                contentPadding = PaddingValues(0.dp),
            ) {
                // CodeButton stacks its content, so the pair needs a row of its own.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share_os),
                        contentDescription = null,
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                    )
                    Text(
                        modifier = Modifier.padding(start = CodeTheme.dimens.staticGrid.x1),
                        text = stringResource(R.string.action_shareProfile),
                        style = CodeTheme.typography.textMedium,
                    )
                }
            }
        }
    }
}

internal object ShareProfileWidgetDefaults {
    /** The card's width in the design. A narrower transcript shrinks it. */
    val WIDTH = 290.dp
    val IDENTITY_WIDTH = 210.dp
    val AVATAR = 64.dp
    val SECTION_GAP = 18.dp
    val BUTTON_RADIUS = 6.dp
    val LINE_GAP = 2.dp

    /** The card's outline: white at 8%, the nearest token to the design's 7%. */
    val STROKE = Color.White.copy(alpha = 0.08f)
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ShareProfileWidgetBubble() {
    ShareProfileWidgetBubble(
        displayName = "Brad Burnham",
        username = "brad_burnham_2",
        profilePicture = null,
        userId = null,
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 320.dp,
        onShare = {},
        modifier = Modifier.padding(16.dp),
    )
}
