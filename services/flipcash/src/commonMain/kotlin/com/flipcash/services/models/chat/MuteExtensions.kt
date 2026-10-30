package com.flipcash.services.models.chat

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Whether the mute is still in force at [now].
 *
 * A [MuteState.Until] lapses with no server signal — the client owns the countdown — so the
 * deadline is compared against the clock on every read. Resolving it once into a stored or
 * rendered boolean would keep a chat muted past the moment it stopped being muted, until some
 * unrelated round trip happened to refresh it.
 */
fun MuteState?.isActiveAt(now: Instant = Clock.System.now()): Boolean = when (this) {
    null -> false
    MuteState.Forever -> true
    is MuteState.Until -> until > now
}

/** Whether the chat is muted for the viewer at [now]. See [isActiveAt]. */
fun ViewerState?.isMutedAt(now: Instant = Clock.System.now()): Boolean =
    this?.mute.isActiveAt(now)
