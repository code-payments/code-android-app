package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
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
 * Cannot act on its own the way [UnmuteChatItem] does: muting takes a shape — a deadline or
 * forever — and there is nothing for this row to ask for until one is picked.
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

/**
 * Shown in [MuteChatItem]'s place while the chat is muted *now*. A timed mute puts the mute row
 * back on its own when its deadline passes — see `rememberIsMuted`, which is what the screen asks.
 */
internal class UnmuteChatItem<T>(override val action: T) : FullMenuItem<T>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Notifications)

    override val name: String
        @Composable get() = stringResource(R.string.title_unmuteChat)
}
