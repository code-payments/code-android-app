package com.flipcash.app.menu.internal

import android.text.format.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * The join date as the profile's stats card shows it: full month and year ("October 2026"), in
 * [locale]'s language and word order. Null when the account has no join date, which the card draws
 * as a dash.
 *
 * The pattern comes from [DateFormat.getBestDateTimePattern] for the `MMMMy` skeleton, so word
 * order and connectives follow [locale] ("octubre de 2026" in Spanish).
 */
internal fun joinedLabel(
    joinedAt: Instant?,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    joinedAt ?: return null
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale)
        .withZone(zone)
        .format(java.time.Instant.ofEpochMilli(joinedAt.toEpochMilliseconds()))
}
