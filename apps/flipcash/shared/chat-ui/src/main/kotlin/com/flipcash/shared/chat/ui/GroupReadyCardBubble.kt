package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * "Your Group Is Ready": the card at the top of a group's transcript while its viewer is the only
 * member. Not a message. It is drawn as an incoming bubble in the share-profile widget's container,
 * with the same filled button, and has no sender, time or reactions.
 */
@Composable
fun GroupReadyCardBubble(
    groupName: String,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val width = minOf(maxWidth, ShareProfileWidgetDefaults.WIDTH)
        val shape = bubbleShape(BubblePosition.Solo, isFromSelf = false)
        Bubble(
            isFromSelf = false,
            position = BubblePosition.Solo,
            minWidth = width,
            maxWidth = width,
            bare = true,
            shape = shape,
            horizontalPadding = CodeTheme.dimens.staticGrid.x3,
            verticalPadding = CodeTheme.dimens.staticGrid.x3,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(White05, shape)
                .border(CodeTheme.dimens.border, ShareProfileWidgetDefaults.STROKE, shape),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.SECTION_GAP),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.LINE_GAP)) {
                    Text(
                        text = stringResource(R.string.title_groupReady),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(R.string.subtitle_groupReady, groupName),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CodeTheme.dimens.staticGrid.x11),
                    onClick = onInvite,
                    buttonState = ButtonState.Filled,
                    shape = RoundedCornerShape(ShareProfileWidgetDefaults.BUTTON_RADIUS),
                    overrideContentPadding = true,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.title_invitePeople),
                        style = CodeTheme.typography.textMedium,
                    )
                }
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
