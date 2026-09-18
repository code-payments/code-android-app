package com.flipcash.services.models.chat

import kotlin.time.Instant

/**
 * A chat's mute duration for a viewer. Mirrors `chat.v1.MuteState`.
 */
sealed class MuteState {
    /** Muted until [until], after which the mute lapses client-side; nothing is sent when it does. */
    data class Until(val until: Instant) : MuteState()

    /** Muted forever, until explicitly unmuted. */
    data object Forever : MuteState()
}
