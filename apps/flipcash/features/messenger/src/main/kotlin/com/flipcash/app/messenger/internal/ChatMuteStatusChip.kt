package com.flipcash.app.messenger.internal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ViewerState
import com.getcode.theme.CodeTheme

/**
 * What the chat's mute currently is, drawn under the title on both chat settings surfaces —
 * "Muted until 5:56 PM" for a timed mute, plain "Muted" for an indefinite one, and nothing at all
 * while the chat is audible.
 *
 * It sits here rather than on the mute row because the row has no space for it, and because a row
 * whose own label reported the state read as a missed tap: the words under the finger changed to
 * the opposite of what was just tapped. The row now says the same thing either way and this says
 * what happened.
 *
 * Stating the deadline is what makes a timed mute trustworthy — without it a timed mute cannot be
 * told apart from an indefinite one, which is the whole reason the picker offers both.
 *
 * Drawn as a chip rather than as another line of secondary text: the lines above it are the chat's
 * own identity — its name, its size, when someone joined — and this is the viewer's setting. Given
 * their styling it read as a fourth fact about the chat. Amber, because a mute is the one thing on
 * the screen that stops being true on its own, and the colour is what separates it from the settled
 * facts above; tinted rather than solid so it stays under the title. The ground is the amber itself
 * at a tenth strength rather than a theme token, because what it has to sit on is whatever the host
 * screen's background is.
 *
 * Takes the [ViewerState] rather than a label so the countdown behind it is this composable's —
 * see [rememberMutedLabel], which drops the text the moment a timed mute lapses with nothing sent
 * from the server to say so.
 *
 * [reserveSpace] false drops the held line, for a header where something else ends the block and
 * an empty line would only read as a gap.
 */
@Composable
internal fun ChatMuteStatusChip(
    viewerState: ViewerState?,
    modifier: Modifier = Modifier,
    reserveSpace: Boolean = true,
) {
    val label = rememberMutedLabel(viewerState)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Holds the line's height whether or not there is a mute, so the rows below don't move the
        // moment one lapses or is cleared — that reads as the screen re-laying itself out rather
        // than one fact going away. Never drawn, never read aloud.
        if (reserveSpace) {
            Chip(
                label = stringResource(R.string.label_muted),
                modifier = Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { },
            )
        }

        // Springs in and fades out. Arriving is the event worth seeing — the user just chose it —
        // so it gets the overshoot; going away is either their unmute or a deadline passing, and
        // neither wants attention drawn to it.
        AnimatedVisibility(
            visible = label != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f, animationSpec = spring()),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            Chip(label = label.orEmpty())
        }
    }
}

@Composable
private fun Chip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = CodeTheme.colors.warning.copy(alpha = 0.1f),
                shape = CircleShape,
            )
            .padding(
                horizontal = CodeTheme.dimens.staticGrid.x2,
                vertical = CodeTheme.dimens.staticGrid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
    ) {
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
            imageVector = Icons.Outlined.NotificationsOff,
            contentDescription = null,
            tint = CodeTheme.colors.warning,
        )
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.warning,
        )
    }
}
