package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Whether the "Your Group Is Ready" card is in the transcript. The spec iOS follows says the same:
 * a group, its roster loaded, the viewer the only member, and the viewer able to invite.
 *
 * Able to invite is `canInvite || !isPrivate`: the server withholds `can_invite` from a public
 * group's creator, yet group info's Share still opens the plain share sheet for them.
 *
 * [rosterResolved] is separate from [memberCount] because an unloaded group reads as a count of
 * zero or a stale one, and a card that flashed up before the roster arrived would leave again.
 */
internal fun showsGroupReadyCard(
    isGroup: Boolean,
    rosterResolved: Boolean,
    memberCount: Long,
    canInvite: Boolean,
    isPrivate: Boolean,
): Boolean = isGroup && rosterResolved && memberCount == 1L && (canInvite || !isPrivate)

/** How long the card takes to leave once someone has joined. */
internal const val GROUP_READY_POP_MILLIS = 280

private const val POP_PEAK_MILLIS = 90
private const val POP_PEAK_SCALE = 1.08f

/**
 * Keeps the card composed while it leaves. The list has no item animations, so when [show] turns
 * false the card is held for [GROUP_READY_POP_MILLIS], scaling 1 -> 1.08 -> 0 and fading out, then
 * [present] goes false and the list drops the item.
 *
 * A card that was never shown does not animate: opening on a group that already has members just
 * leaves [present] false. And only a join pops it; losing the right to invite removes it at once.
 */
@Stable
internal class GroupReadyCardPresence(initiallyPresent: Boolean) {
    var present by mutableStateOf(initiallyPresent)
        private set
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    suspend fun update(show: Boolean, joined: Boolean) {
        if (show) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            present = true
        } else if (present) {
            if (joined) {
                coroutineScope {
                    launch {
                        scale.animateTo(0f, keyframes {
                            durationMillis = GROUP_READY_POP_MILLIS
                            1f at 0
                            POP_PEAK_SCALE at POP_PEAK_MILLIS using FastOutSlowInEasing
                            0f at GROUP_READY_POP_MILLIS using FastOutSlowInEasing
                        })
                    }
                    launch {
                        alpha.animateTo(0f, keyframes {
                            durationMillis = GROUP_READY_POP_MILLIS
                            1f at 0
                            1f at POP_PEAK_MILLIS using LinearEasing
                            0f at GROUP_READY_POP_MILLIS using LinearEasing
                        })
                    }
                }
            }
            present = false
        }
    }
}

@Composable
internal fun rememberGroupReadyCardPresence(show: Boolean, joined: Boolean): GroupReadyCardPresence {
    val presence = remember { GroupReadyCardPresence(initiallyPresent = show) }
    LaunchedEffect(show) { presence.update(show, joined) }
    return presence
}
