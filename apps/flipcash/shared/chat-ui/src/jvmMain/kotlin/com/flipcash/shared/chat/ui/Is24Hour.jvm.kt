package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal actual fun systemIs24HourClock(): Boolean = remember {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.getDefault(),
    )
    // 12-hour locales carry an am/pm marker ('a'); 24-hour ones do not.
    !pattern.contains('a')
}
