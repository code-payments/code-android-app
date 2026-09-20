package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.messenger.R

/**
 * The row that opens the duration picker.
 *
 * Generic over the action because the two profiles that offer it — a group's and a DM's — name
 * their own actions, and a [com.flipcash.app.menu.MenuItem] carries one action type. Muting is the
 * same thing on both, so it is one row definition rather than two that can drift apart; what
 * differs is only what the screen does with the tap.
 *
 * One row, reading the same either way, whether or not the chat is currently muted: the picker it
 * opens is where unmuting lives too. A row that became "Unmute Notifications" under the tap that
 * muted the chat read as a missed tap — the words under the finger had turned into the opposite of
 * what was just chosen — and it left the row with nowhere to say *until when*. What the mute
 * currently is belongs to [com.flipcash.app.messenger.internal.ChatMuteStatusChip], under the
 * profile's title.
 *
 * Declare instances as top-level `val`s, naming the action type explicitly. [FullMenuItem] mints an
 * id per instance, so one built inside a composition would get a new one on every recomposition;
 * and inference off a `data object` argument picks that object's own type rather than the sealed
 * interface the menu is typed on.
 */
internal class MuteChatItem<T>(override val action: T) : FullMenuItem<T>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.NotificationsOff)

    override val name: String
        @Composable get() = stringResource(R.string.title_muteChat)
}
