package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R

/**
 * "Your Group Is Ready": the card at the top of a group's transcript while its viewer is the only
 * member. Not a message. It is the share-profile widget's bubble ([WidgetCardBubble]) with its own
 * words and no avatar, and has no sender, time or reactions.
 */
@Composable
fun GroupReadyCardBubble(
    groupName: String,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        WidgetCardBubble(
            title = stringResource(R.string.title_groupReady),
            subtitle = stringResource(R.string.subtitle_groupReady, groupName),
            buttonLabel = stringResource(R.string.title_invitePeople),
            onClick = onInvite,
            isFromSelf = false,
            position = BubblePosition.Solo,
            maxWidth = maxWidth,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupReadyCardBubble() {
    GroupReadyCardBubble(groupName = "Brothers", onInvite = {}, modifier = Modifier.padding(16.dp))
}
