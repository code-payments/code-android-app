package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.isMutedAt
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * The bell drawn wherever a chat is named, while that chat is muted right now.
 *
 * Takes the viewer state rather than a muted boolean on purpose. A timed mute lapses with nothing
 * sent to say so, so a caller that resolved the answer itself would hand this a value that is
 * already wrong by the time the deadline passes; [rememberIsMuted] waits the deadline out and stops
 * drawing on its own. Emits nothing at all when the chat is audible, so a row pays no layout for it.
 *
 * Sized and tinted to sit with the caption text it stands beside — the row's timestamp, the title
 * bar's name — rather than to be noticed on its own. It states a setting, it does not ask for
 * anything.
 */
@Composable
fun MutedIndicator(
    viewerState: ViewerState?,
    modifier: Modifier = Modifier,
) {
    if (!rememberIsMuted(viewerState)) return
    Icon(
        modifier = modifier.size(CodeTheme.dimens.staticGrid.x4),
        imageVector = Icons.Outlined.NotificationsOff,
        // Reaches a screen reader through the merged label of whatever container holds it —
        // Compose concatenates a child's description into its parent's rather than dropping it,
        // so the row reads its name, its timestamp and then this.
        contentDescription = stringResource(R.string.label_muted),
        tint = CodeTheme.colors.textSecondary,
    )
}

/**
 * Whether [viewerState] is muted right now, re-answered the moment a timed mute lapses.
 *
 * A composable rather than a plain read because the lapse has no event behind it. Nothing arrives
 * from the server when a deadline passes, and nothing writes the row, so a screen that read the
 * state once would keep showing a chat as muted after it had gone audible. This waits out the
 * remaining time and then answers false.
 */
@Composable
fun rememberIsMuted(viewerState: ViewerState?): Boolean {
    val mute = viewerState?.mute
    return produceState(initialValue = viewerState.isMutedAt(), mute) {
        value = viewerState.isMutedAt()
        val until = (mute as? MuteState.Until)?.until ?: return@produceState
        val remaining = until - Clock.System.now()
        if (remaining.isPositive()) delay(remaining)
        value = false
    }.value
}
