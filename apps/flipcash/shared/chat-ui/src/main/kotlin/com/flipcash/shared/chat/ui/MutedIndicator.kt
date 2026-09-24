package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Sized and tinted to sit quietly beside the name it follows — the chat list row's, the title
 * bar's name — rather than to be noticed on its own. It states a setting, it does not ask for
 * anything. Filled rather than outlined, matching iOS' `bell.slash.fill`. The mute row and the
 * profile chip stay outlined, as iOS' do — those sit at body size beside a label that already
 * names the setting, where this one has to carry it alone at caption size.
 */
@Composable
fun MutedIndicator(
    viewerState: ViewerState?,
    modifier: Modifier = Modifier,
) {
    if (!rememberIsMuted(viewerState)) return
    Icon(
        // Caption scale, a touch under the 14sp subtitle it trails and well under the 16sp title
        // bar name. A Material glyph fills its box where the SF Symbol iOS draws carries its own
        // padding, so matching iOS by the box size alone lands a visibly heavier bell.
        modifier = modifier.size(CodeTheme.dimens.staticGrid.x3),
        imageVector = Icons.Filled.NotificationsOff,
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
    // Only the lapse is waited for. The state itself is read in the same pass it changes in: a
    // producer re-answering it would hold the previous answer for a frame, which on an unmute is a
    // frame of "muted" with no deadline, drawn as a plain "Muted".
    var lapsed by remember(mute) { mutableStateOf(false) }
    LaunchedEffect(mute) {
        val until = (mute as? MuteState.Until)?.until ?: return@LaunchedEffect
        val remaining = until - Clock.System.now()
        if (remaining.isPositive()) delay(remaining)
        lapsed = true
    }
    return !lapsed && viewerState.isMutedAt()
}
