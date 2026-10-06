package com.flipcash.app.menu.internal

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * The join date as the profile's stats card shows it: full month and year ("October 2026"), in
 * [locale]'s language and word order. Null when the account has no join date, which the card draws
 * as a dash.
 *
 * `L` rather than `M` for the month: it is the stand-alone form, which is the right one when the
 * month is not part of a longer date and differs from the in-sentence form in some languages.
 * [DateUtils.getDate] is not used because it pins the locale to English.
 */
internal fun joinedLabel(
    joinedAt: Instant?,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    joinedAt ?: return null
    return DateTimeFormatter.ofPattern("LLLL y", locale)
        .withZone(zone)
        .format(java.time.Instant.ofEpochMilli(joinedAt.toEpochMilliseconds()))
}
