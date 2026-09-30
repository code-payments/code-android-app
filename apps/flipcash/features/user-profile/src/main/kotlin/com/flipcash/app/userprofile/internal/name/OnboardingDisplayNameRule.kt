package com.flipcash.app.userprofile.internal.name

internal const val ONBOARDING_DISPLAY_NAME_MAX_LENGTH = 64

/**
 * The onboarding display-name rule, shared in definition with iOS: after trimming leading and
 * trailing whitespace the name is non-empty, at most [ONBOARDING_DISPLAY_NAME_MAX_LENGTH]
 * characters, and made only of ASCII letters, ASCII digits and the space character (U+0020).
 * Any other whitespace (tab, newline, NBSP), punctuation, accented letters or emoji is invalid.
 * Runs of internal spaces are allowed.
 *
 * Pure: no Android types, so it can move to shared code.
 */
internal fun isValidOnboardingDisplayName(raw: String): Boolean {
    val name = raw.trim()
    return name.isNotEmpty() &&
        name.length <= ONBOARDING_DISPLAY_NAME_MAX_LENGTH &&
        name.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' }
}
