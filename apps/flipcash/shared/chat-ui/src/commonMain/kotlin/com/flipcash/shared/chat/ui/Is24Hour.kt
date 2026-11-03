package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Overrides whether times render on a 24-hour clock. Left unset, [is24HourClock] asks the platform:
 * Android's `DateFormat.is24HourFormat`, and on desktop the pattern the default locale uses for a
 * short time. Tests and previews provide a value to pin it.
 */
val LocalIs24Hour = compositionLocalOf<Boolean?> { null }

/** Whether times of day render as "18:13" rather than "6:13 PM". */
@Composable
fun is24HourClock(): Boolean = LocalIs24Hour.current ?: systemIs24HourClock()

@Composable
internal expect fun systemIs24HourClock(): Boolean
