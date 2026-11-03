package com.flipcash.shared.chat.ui

// kmp-audit: needs a bubble or link card that renders CodeButton, which lives in :ui:components androidMain (RuntimeShader frosted glass)

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.ic_flipcash_logo
import com.flipcash.core.resources.subtitle_groupReady
import com.flipcash.core.resources.title_flipcashSender
import com.flipcash.core.resources.title_groupReady
import com.flipcash.core.resources.title_invitePeople
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import com.getcode.theme.CodeTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper

/**
 * "Your Group Is Ready": the card at the top of a group's transcript while its viewer is the only
 * member. Not a message. It is the share-profile widget's bubble ([WidgetCardBubble]) with its own
 * words and Flipcash's logo and name as its sender, and has no time or reactions.
 */
@Composable
fun GroupReadyCardBubble(
    groupName: String,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Attributed to Flipcash like an incoming group message: the name over the run, the logo in the
    // gutter the member avatars use. The words are the app's, so there is no member behind it.
    val gutter = CodeTheme.dimens.staticGrid.x5
    val gap = CodeTheme.dimens.grid.x1
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.title_flipcashSender),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            modifier = Modifier.padding(start = gutter + gap + gap, bottom = gap),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            // The mark on the standard avatar disc, as a member without a picture gets.
            Box(
                modifier = Modifier
                    .size(gutter)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(CodeTheme.colors.contactAvatar.colors))
                    .padding(gap),
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_flipcash_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                WidgetCardBubble(
                    title = stringResource(Res.string.title_groupReady),
                    subtitle = stringResource(Res.string.subtitle_groupReady, groupName),
                    buttonLabel = stringResource(Res.string.title_invitePeople),
                    onClick = onInvite,
                    isFromSelf = false,
                    position = BubblePosition.Solo,
                    maxWidth = maxWidth,
                    messageTypography = true,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupReadyCardBubble() {
    GroupReadyCardBubble(groupName = "Brothers", onInvite = {}, modifier = Modifier.padding(16.dp))
}
