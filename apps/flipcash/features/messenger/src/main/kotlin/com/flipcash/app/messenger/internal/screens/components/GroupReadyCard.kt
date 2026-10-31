package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import com.flipcash.shared.chat.ui.groupAwaitsInvites
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Whether the "Your Group Is Ready" card is in the transcript; see [groupAwaitsInvites]. */
internal fun showsGroupReadyCard(
    isGroup: Boolean,
    rosterResolved: Boolean,
    memberCount: Long,
    canInvite: Boolean,
    isPrivate: Boolean,
): Boolean = groupAwaitsInvites(isGroup, rosterResolved, memberCount, canInvite, isPrivate)

/** How long the card takes to leave once someone has joined: the row is held this long. */
internal const val GROUP_READY_EXIT_MILLIS = 320

/** The card fades over the first part of that; the rest of the transcript slides up as it shrinks. */
internal const val GROUP_READY_FADE_MILLIS = 200

/**
 * Keeps the card composed while it leaves. When [show] turns false the card is held for
 * [GROUP_READY_EXIT_MILLIS]: it fades out over [GROUP_READY_FADE_MILLIS] while its row collapses
 * from full height to none, so whatever sits below it slides into the freed space instead of
 * jumping. Then [present] goes false and the list drops the item.
 *
 * The collapse is the row's own height rather than a placement animation on every other row: the
 * list has no item animations, and turning them on for the whole transcript would change how new
 * messages arrive too.
 *
 * A card that was never shown does not animate: opening on a group that already has members just
 * leaves [present] false. And only a join animates it; losing the right to invite removes it at once.
 */
@Stable
internal class GroupReadyCardPresence(initiallyPresent: Boolean) {
    var present by mutableStateOf(initiallyPresent)
        private set
    /** 1 while the card is up, falling to 0 as its row collapses. */
    val heightFraction = Animatable(1f)
    val alpha = Animatable(1f)

    suspend fun update(show: Boolean, joined: Boolean) {
        if (show) {
            heightFraction.snapTo(1f)
            alpha.snapTo(1f)
            present = true
        } else if (present) {
            if (joined) {
                coroutineScope {
                    launch {
                        heightFraction.animateTo(
                            0f,
                            tween(GROUP_READY_EXIT_MILLIS, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        alpha.animateTo(0f, tween(GROUP_READY_FADE_MILLIS, easing = LinearEasing))
                    }
                }
            }
            present = false
        }
    }
}

/** Reports [fraction] of the content's height and clips what hangs outside it, so a list sees the row shrink. */
internal fun Modifier.collapseHeight(fraction: () -> Float): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        }

@Composable
internal fun rememberGroupReadyCardPresence(show: Boolean, joined: Boolean): GroupReadyCardPresence {
    val presence = remember { GroupReadyCardPresence(initiallyPresent = show) }
    LaunchedEffect(show) { presence.update(show, joined) }
    return presence
}
