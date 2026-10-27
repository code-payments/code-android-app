package com.flipcash.app.messenger.internal

import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.SilenceState
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What the silence picker offers: the same durations as [MuteOption], worded for the member.
 *
 * Its own enum rather than reusing [MuteOption] because the labels differ ("Forever" where mute
 * says "Always") and because a silence is a different request with a different result.
 */
internal enum class SilenceOption(
    /** How long from now the silence runs, or `null` for the indefinite option. */
    val duration: Duration?,
    val labelRes: Int,
) {
    OneHour(1.hours, R.string.action_muteFor_oneHour),
    EightHours(8.hours, R.string.action_muteFor_eightHours),
    OneWeek(7.days, R.string.action_muteFor_oneWeek),
    Forever(null, R.string.action_silenceFor_forever);

    /** The silence this option asks for, measured from [now] on the tap rather than when the sheet opened. */
    fun toSilenceState(now: Instant = Clock.System.now()): SilenceState =
        duration?.let { SilenceState.Until(now + it) } ?: SilenceState.Forever
}
